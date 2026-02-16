package com.example.inventoryapp.data.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.local.SystemAlertType
import com.example.inventoryapp.data.remote.model.AlertResponseDto
import com.example.inventoryapp.data.remote.model.AlertTypeDto
import com.example.inventoryapp.ui.common.ActivityTracker
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.ui.common.SystemAlertManager
import com.google.gson.Gson
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object AlertsWebSocketManager {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _alerts = MutableSharedFlow<AlertResponseDto>(extraBufferCapacity = 8)
    val alerts = _alerts.asSharedFlow()
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var currentToken: String? = null
    @Volatile private var manualClose = false
    @Volatile private var reconnectAttempts = 0
    @Volatile private var reconnectJobActive = false
    @Volatile private var currentDialog: AlertDialog? = null
    @Volatile private var lastWsStatusAlertAt: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingQueue = ArrayDeque<AlertPopupData>()
    private val recentAlertKeys = LinkedHashMap<String, Long>()
    @Volatile private var showingPopup = false
    @Volatile private var shownCountInBatch = 0

    private const val WS_STATUS_ALERT_COOLDOWN_MS = 30_000L
    private const val ALERT_DEDUP_WINDOW_MS = 8_000L
    private const val ALERT_DEDUP_MAX_KEYS = 300

    fun connect(context: Context) {
        if (shouldDeferRealtimeReconnect()) return
        val token = SessionManager(context).getToken() ?: return
        if (socket != null && token == currentToken) return

        disconnect()
        manualClose = false
        currentToken = token

        val url = NetworkModule.buildWsUrl("/ws/alerts?token=$token")
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, listener(context.applicationContext))
    }

    fun disconnect() {
        manualClose = true
        socket?.close(1000, "closed")
        socket = null
        currentToken = null
    }

    private fun listener(appContext: Context): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                reconnectJobActive = false
                webSocket.send("ping")
                if (shouldEmitWsStatusAlert()) {
                    SystemAlertManager.record(
                        appContext,
                        SystemAlertType.NETWORK,
                        "WebSocket conectado",
                        "Conexion en tiempo real activa.",
                        blocking = false
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val alert = runCatching { gson.fromJson(text, AlertResponseDto::class.java) }.getOrNull()
                if (alert != null && shouldProcessRealtimeAlert(alert)) {
                    handleAlert(appContext, alert)
                    _alerts.tryEmit(alert)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                if (!manualClose) {
                    if (shouldEmitWsStatusAlert()) {
                        SystemAlertManager.record(
                            appContext,
                            SystemAlertType.NETWORK,
                            "WebSocket desconectado",
                            "No se pudo mantener la conexion en tiempo real.",
                            blocking = false
                        )
                    }
                    scheduleReconnect(appContext)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                if (!manualClose) {
                    if (shouldEmitWsStatusAlert()) {
                        SystemAlertManager.record(
                            appContext,
                            SystemAlertType.NETWORK,
                            "WebSocket desconectado",
                            "La conexion en tiempo real se ha cerrado.",
                            blocking = false
                        )
                    }
                    scheduleReconnect(appContext)
                }
            }
        }
    }

    private fun scheduleReconnect(context: Context) {
        if (reconnectJobActive) return
        if (shouldDeferRealtimeReconnect()) return
        reconnectJobActive = true
        val delayMs = reconnectDelayMs()
        scope.launch {
            delay(delayMs)
            reconnectJobActive = false
            if (shouldDeferRealtimeReconnect()) return@launch
            connect(context)
        }
    }

    private fun reconnectDelayMs(): Long {
        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(6)
        val base = 1000L shl reconnectAttempts
        return base.coerceAtMost(30_000L)
    }

    private fun shouldDeferRealtimeReconnect(): Boolean {
        return NetworkModule.isManualOffline() || NetworkModule.offlineState.value
    }

    private fun shouldEmitWsStatusAlert(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWsStatusAlertAt < WS_STATUS_ALERT_COOLDOWN_MS) return false
        lastWsStatusAlertAt = now
        return true
    }

    private fun shouldProcessRealtimeAlert(alert: AlertResponseDto): Boolean {
        val key = listOf(
            alert.id.toString(),
            alert.stockId?.toString().orEmpty(),
            alert.alertType.name,
            alert.alertStatus.name,
            alert.quantity.toString(),
            alert.minQuantity.toString()
        ).joinToString("|")

        val now = System.currentTimeMillis()
        synchronized(recentAlertKeys) {
            val iterator = recentAlertKeys.entries.iterator()
            while (iterator.hasNext()) {
                val (_, ts) = iterator.next()
                if (now - ts > ALERT_DEDUP_WINDOW_MS) iterator.remove()
            }

            val seenAt = recentAlertKeys[key]
            if (seenAt != null && now - seenAt <= ALERT_DEDUP_WINDOW_MS) {
                return false
            }

            recentAlertKeys[key] = now
            while (recentAlertKeys.size > ALERT_DEDUP_MAX_KEYS) {
                val firstKey = recentAlertKeys.entries.firstOrNull()?.key ?: break
                recentAlertKeys.remove(firstKey)
            }
            return true
        }
    }

    private fun handleAlert(context: Context, alert: AlertResponseDto) {
        val title = when (alert.alertType) {
            AlertTypeDto.LOW_STOCK -> "Alerta de stock bajo"
            AlertTypeDto.OUT_OF_STOCK -> "Alerta de stock agotado"
            AlertTypeDto.LARGE_MOVEMENT -> "Alerta de movimiento grande"
            AlertTypeDto.TRANSFER_COMPLETE -> "Alerta de transferencia completada"
            AlertTypeDto.IMPORT_ISSUES -> "Alerta de importacion con errores"
        }

        val iconRes = when (alert.alertType) {
            AlertTypeDto.OUT_OF_STOCK -> R.drawable.alert_red
            AlertTypeDto.LOW_STOCK -> R.drawable.alert_yellow
            AlertTypeDto.TRANSFER_COMPLETE -> R.drawable.alert_green
            AlertTypeDto.LARGE_MOVEMENT -> R.drawable.alert_violet
            AlertTypeDto.IMPORT_ISSUES -> R.drawable.alert_blue
        }
        val cardColor = when (alert.alertType) {
            AlertTypeDto.OUT_OF_STOCK -> 0xFFFFEBEE.toInt()
            AlertTypeDto.LOW_STOCK -> 0xFFFFF8E1.toInt()
            AlertTypeDto.TRANSFER_COMPLETE -> 0xFFE8F5E9.toInt()
            AlertTypeDto.LARGE_MOVEMENT -> 0xFFF3E5F5.toInt()
            AlertTypeDto.IMPORT_ISSUES -> 0xFFE3F2FD.toInt()
        }

        scope.launch {
            val extraDetails = fetchStockDetails(alert)
            val details = when (alert.alertType) {
                AlertTypeDto.LOW_STOCK -> "Cantidad: ${alert.quantity}\nMinimo: ${alert.minQuantity}"
                AlertTypeDto.OUT_OF_STOCK -> "Cantidad: ${alert.quantity}"
                AlertTypeDto.LARGE_MOVEMENT -> "Unidades: ${alert.quantity}"
                AlertTypeDto.TRANSFER_COMPLETE -> "Unidades: ${alert.quantity}"
                AlertTypeDto.IMPORT_ISSUES -> "Incidencias: ${alert.quantity}"
            }
            val message = SpannableStringBuilder()
            if (extraDetails != null) {
                message.append(extraDetails)
                message.append("\n")
            }
            message.append(details)
            val styledMessage = emphasizeProductLine(message)

            val activity = ActivityTracker.getCurrent()
            if (activity != null) {
                enqueuePopup(
                    activity,
                    AlertPopupData(
                        title = title,
                        message = styledMessage,
                        iconRes = iconRes,
                        cardColor = cardColor
                    )
                )

                val badge = activity.findViewById<TextView>(R.id.tvAlertsBadge)
                val owner = activity as? LifecycleOwner
                if (badge != null && owner != null) {
                    AlertsBadgeUtil.refresh(owner.lifecycleScope, badge)
                }
            } else {
                val toast = Toast.makeText(context, "$title\n${message}", Toast.LENGTH_SHORT)
                toast.setGravity(Gravity.CENTER, 0, 0)
                val view = toast.view
                if (view != null) {
                    view.background = ContextCompat.getDrawable(context, R.drawable.bg_snackbar)
                    val text = view.findViewById<TextView>(android.R.id.message)
                    text?.setTextColor(android.graphics.Color.WHITE)
                }
                toast.show()
            }
        }
    }

    private fun enqueuePopup(activity: android.app.Activity, data: AlertPopupData) {
        activity.runOnUiThread {
            pendingQueue.add(data)
            showNextPopup(activity)
        }
    }

    private fun showNextPopup(activity: android.app.Activity) {
        if (showingPopup) return
        val next = pendingQueue.removeFirstOrNull() ?: return
        showingPopup = true
        shownCountInBatch += 1

        val total = pendingQueue.size + shownCountInBatch
        val counterText = if (total > 1) "${shownCountInBatch} de $total" else ""

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_alert_popup, null)
        val ivIcon = view.findViewById<ImageView>(R.id.ivAlertIcon)
        val card = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAlertPopup)
        val tvTitle = view.findViewById<TextView>(R.id.tvAlertTitle)
        val tvCounter = view.findViewById<TextView>(R.id.tvAlertCounter)
        val tvMessage = view.findViewById<TextView>(R.id.tvAlertMessage)
        val btnClose = view.findViewById<Button>(R.id.btnAlertClose)
        ivIcon.setImageResource(next.iconRes)
        val pulse = AnimationUtils.loadAnimation(activity, R.anim.alert_icon_pulse)
        ivIcon.startAnimation(pulse)
        card.setCardBackgroundColor(next.cardColor)
        tvTitle.text = next.title
        tvCounter.text = counterText
        tvCounter.visibility = if (counterText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        tvMessage.text = next.message

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.AlertPopupAnimation
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.55f)
        currentDialog = dialog
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            showingPopup = false
            if (pendingQueue.isNotEmpty()) {
                showNextPopup(activity)
            } else {
                shownCountInBatch = 0
            }
        }
        dialog.show()
        mainHandler.postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }, 10_000L)
    }

    private suspend fun fetchStockDetails(alert: AlertResponseDto): String? {
        val stockId = alert.stockId ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val stockRes = NetworkModule.api.getStock(stockId)
                if (!stockRes.isSuccessful || stockRes.body() == null) return@withContext "Stock ID: $stockId"
                val stock = stockRes.body()!!
                val productRes = NetworkModule.api.getProduct(stock.productId)
                val productName = if (productRes.isSuccessful && productRes.body() != null) {
                    productRes.body()!!.name
                } else {
                    "Producto ${stock.productId}"
                }
                val locationLabel = stock.location ?: "N/D"
                "Producto: $productName (ID ${stock.productId})\nUbicacion: $locationLabel\nStock ID: $stockId"
            } catch (_: Exception) {
                "Stock ID: $stockId"
            }
        }
    }

    private fun emphasizeProductLine(text: SpannableStringBuilder): SpannableStringBuilder {
        val marker = "Producto: "
        val start = text.indexOf(marker)
        if (start == -1) return text
        val lineEnd = text.indexOf("\n", start).let { if (it == -1) text.length else it }
        val boldStart = start + marker.length
        if (boldStart >= lineEnd) return text
        text.setSpan(
            StyleSpan(android.graphics.Typeface.BOLD),
            boldStart,
            lineEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return text
    }

    private data class AlertPopupData(
        val title: String,
        val message: SpannableStringBuilder,
        val iconRes: Int,
        val cardColor: Int
    )
}
