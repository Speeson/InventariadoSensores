package com.example.inventoryapp.ui.scan
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.databinding.ActivityConfirmScanBinding
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType
import com.example.inventoryapp.data.repository.remote.RemoteScanRepository
import com.example.inventoryapp.data.repository.remote.ScanSendResult
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R
import java.text.NumberFormat
import java.util.Locale

class ConfirmScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmScanBinding
    private val repo = RemoteScanRepository()
    private var selectedType: MovementType = MovementType.OUT
    private var productExists: Boolean = true
    private var isOfflineMode: Boolean = false
    private val gson = Gson()
    private var productId: Int? = null
    private var productName: String? = null
    private var lastQuantity: Int = 0
    private var lastLocationRaw: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        applyConfirmTitleGradient()
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        val snack = SendSnack(binding.root)
        val barcode = intent.getStringExtra("barcode").orEmpty()
        isOfflineMode = intent.getBooleanExtra("offline", false)
        binding.tvBarcode.text = "Codigo de barras: $barcode"

        applyTypeSelection(MovementType.OUT)
        binding.btnTypeIn.setOnClickListener { applyTypeSelection(MovementType.IN) }
        binding.btnTypeOut.setOnClickListener { applyTypeSelection(MovementType.OUT) }

        setupLocationDropdown()
        binding.tilLocation.post { applyLocationDropdownIcon() }

        if (isOfflineMode) {
            binding.tvProductName.text = "Sin conexion: validacion pendiente"
            binding.tvProductName.setTextColor(
                ContextCompat.getColor(this@ConfirmScanActivity, android.R.color.holo_orange_dark)
            )
            productExists = true
        } else {
            lifecycleScope.launch {
                try {
                    val res = NetworkModule.api.listProducts(barcode = barcode, limit = 1, offset = 0)
                    if (res.isSuccessful && res.body() != null && res.body()!!.items.isNotEmpty()) {
                        val p = res.body()!!.items.first()
                        productId = p.id
                        productName = p.name
                        binding.tvProductName.text = "${p.name} (SKU ${p.sku})"
                        productExists = true
                    } else {
                        binding.tvProductName.text = "Producto no encontrado"
                        binding.tvProductName.setTextColor(
                            ContextCompat.getColor(this@ConfirmScanActivity, android.R.color.holo_red_dark)
                        )
                        productExists = false
                        showProductNotFoundWarning(barcode)
                    }
                } catch (_: Exception) {
                    isOfflineMode = true
                    binding.tvProductName.text = "Sin conexion: validacion pendiente"
                    binding.tvProductName.setTextColor(
                        ContextCompat.getColor(this@ConfirmScanActivity, android.R.color.holo_orange_dark)
                    )
                    productExists = true
                    snack.showError("Sin conexion. Se enviara cuando vuelvas a estar online")
                }
            }
        }

        binding.btnConfirm.setOnClickListener {
            if (!productExists && !isOfflineMode) {
                showProductNotFoundWarning(barcode)
                return@setOnClickListener
            }

            val quantity = binding.etQuantity.text.toString().toIntOrNull() ?: 0
            if (quantity <= 0) {
                binding.etQuantity.error = "Cantidad > 0"
                return@setOnClickListener
            }

            lastQuantity = quantity
            lastLocationRaw = binding.etLocation.text.toString().trim()
            val location = normalizeLocationInput(lastLocationRaw).ifEmpty { "default" }
            val movement = Movement(
                barcode = barcode,
                type = selectedType,
                quantity = quantity,
                location = location
            )

            binding.btnConfirm.isEnabled = false
            lifecycleScope.launch {
                try {
                    if (isOfflineMode) {
                        enqueueOffline(movement)
                        showSendResultDialog(
                            success = true,
                            msg = "Guardado offline. Se enviara al reconectar"
                        ) { goBackToScan() }
                    } else {
                        val result = repo.sendFromBarcode(movement)
                        if (result.isSuccess) {
                            val payload = result.getOrNull()
                            val status = awaitFinalEventStatus(payload).uppercase(Locale.ROOT)
                            if (status == "FAILED" || status == "ERROR") {
                                showSendResultDialog(
                                    success = false,
                                    msg = "El evento se envio pero quedo en estado fallido. Puedes revisarlo en Alertas o Actividades.",
                                    eventStatus = payload?.status
                                )
                            } else {
                                showSendResultDialog(
                                    success = true,
                                    msg = buildSendMessage(payload),
                                    eventStatus = payload?.status
                                ) { goBackToScan() }
                            }
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is java.io.IOException) {
                                enqueueOffline(movement)
                                showSendResultDialog(
                                    success = true,
                                    msg = "Guardado offline. Se enviara al reconectar"
                                ) { goBackToScan() }
                            } else {
                                showSendResultDialog(
                                    success = false,
                                    msg = error?.message ?: "Error"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is java.io.IOException) {
                        enqueueOffline(movement)
                        showSendResultDialog(
                            success = true,
                            msg = "Guardado offline. Se enviara al reconectar"
                        ) { goBackToScan() }
                    } else {
                        showSendResultDialog(
                            success = false,
                            msg = e.message ?: "Error"
                        )
                    }
                } finally {
                    binding.btnConfirm.isEnabled = true
                }
            }
        }
    }

    private fun enqueueOffline(movement: Movement) {
        val payload = OfflineSyncer.ScanEventPayload(
            barcode = movement.barcode,
            type = movement.type,
            quantity = movement.quantity,
            location = movement.location?.ifBlank { "default" } ?: "default",
            source = "SCAN"
        )
        OfflineQueue(this).enqueue(PendingType.SCAN_EVENT, gson.toJson(payload))
    }

    private fun setupLocationDropdown() {
        binding.etLocation.apply {
            showSoftInputOnFocus = false
            isCursorVisible = false
            setDropDownBackgroundDrawable(
                ContextCompat.getDrawable(this@ConfirmScanActivity, R.drawable.bg_liquid_dropdown_popup)
            )
            setOnClickListener {
                hideKeyboard()
                showDropDown()
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    hideKeyboard()
                    showDropDown()
                }
            }
        }

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val values = items.sortedBy { it.id }
                        .map { "(${it.id}) ${it.code}" }
                        .distinct()
                    val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = object : ArrayAdapter<String>(
                        this@ConfirmScanActivity,
                        android.R.layout.simple_list_item_1,
                        allValues
                    ) {
                        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                            val view = super.getView(position, convertView, parent)
                            val text = view.findViewById<TextView>(android.R.id.text1)
                            text?.setTextColor(ContextCompat.getColor(this@ConfirmScanActivity, R.color.liquid_popup_list_text))
                            view.setBackgroundResource(R.drawable.bg_liquid_dropdown_item)
                            return view
                        }

                        override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                            val view = super.getDropDownView(position, convertView, parent)
                            val text = view.findViewById<TextView>(android.R.id.text1)
                            text?.setTextColor(ContextCompat.getColor(this@ConfirmScanActivity, R.color.liquid_popup_list_text))
                            view.setBackgroundResource(R.drawable.bg_liquid_dropdown_item)
                            return view
                        }
                    }
                    binding.etLocation.setAdapter(adapter)
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
            }
        }
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun applyTypeSelection(type: MovementType) {
        selectedType = type
        val isIn = type == MovementType.IN
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val normalText = if (isDark) {
            ContextCompat.getColor(this, R.color.liquid_popup_body)
        } else {
            ContextCompat.getColor(this, R.color.liquid_popup_button_text)
        }
        val selectedText = if (isDark) {
            ContextCompat.getColor(this, android.R.color.white)
        } else {
            ContextCompat.getColor(this, R.color.liquid_popup_button_text)
        }

        binding.btnTypeIn.setBackgroundResource(
            if (isIn) R.drawable.bg_liquid_button_pressed else R.drawable.bg_liquid_button_pressable
        )
        binding.btnTypeOut.setBackgroundResource(
            if (!isIn) R.drawable.bg_liquid_button_pressed else R.drawable.bg_liquid_button_pressable
        )
        binding.btnTypeIn.setTextColor(if (isIn) selectedText else normalText)
        binding.btnTypeOut.setTextColor(if (!isIn) selectedText else normalText)
    }

    private fun buildSendMessage(payload: ScanSendResult?): String {
        if (payload == null) return "Evento enviado"
        val name = productName ?: payload.productName
        val idPart = productId?.let { " (ID $it)" } ?: ""
        val locationDisplay = formatLocationDisplay(lastLocationRaw)
        val quantityDisplay = formatQuantity(lastQuantity)
        return "Producto: $name$idPart\nUbicacion: $locationDisplay\nCantidad: $quantityDisplay"
    }

    private fun formatLocationDisplay(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "-"
        val match = Regex("^\\((\\d+)\\)\\s*(.+)$").find(trimmed)
        return if (match != null) {
            "${match.groupValues[2]} (ID ${match.groupValues[1]})"
        } else {
            trimmed
        }
    }

    private fun formatQuantity(value: Int): String {
        if (value <= 0) return "-"
        return NumberFormat.getInstance(Locale("es", "ES")).format(value)
    }

    private fun applyLocationDropdownIcon() {
        binding.tilLocation.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, com.example.inventoryapp.R.drawable.triangle_down_lg)
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun applyConfirmTitleGradient() {
        binding.tvConfirmTitle.post {
            val paint = binding.tvConfirmTitle.paint
            val width = paint.measureText(binding.tvConfirmTitle.text.toString())
            if (width <= 0f) return@post
            val c1 = ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_start)
            val c2 = ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid2)
            val c3 = ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid1)
            val c4 = ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_end)
            val shader = android.graphics.LinearGradient(
                0f,
                0f,
                width,
                0f,
                intArrayOf(c1, c2, c3, c4),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            binding.tvConfirmTitle.invalidate()
        }
    }

    private fun showProductNotFoundWarning(barcode: String) {
        val view = layoutInflater.inflate(R.layout.dialog_liquid_scan_warning, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<TextView>(R.id.tvScanWarnMessage).text =
            "No se ha encontrado un producto con el codigo de barras $barcode."
        view.findViewById<Button>(R.id.btnScanWarnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        dialog.window?.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showSendResultDialog(
        success: Boolean,
        msg: String,
        eventStatus: String? = null,
        onConfirm: (() -> Unit)? = null
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_liquid_scan_result, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        val title = view.findViewById<TextView>(R.id.tvScanResultTitle)
        val message = view.findViewById<TextView>(R.id.tvScanResultMessage)
        val lottie = view.findViewById<LottieAnimationView>(R.id.lottieScanResult)
        val btn = view.findViewById<Button>(R.id.btnScanResultConfirm)

        if (success) {
            title.text = "Evento guardado"
            lottie.setAnimation(R.raw.correct_create)
        } else {
            title.text = "Error al enviar"
            lottie.setAnimation(R.raw.wrong)
        }
        lottie.playAnimation()

        val normalizedStatus = eventStatus?.uppercase(Locale.ROOT)
        val statusMsg = when (normalizedStatus) {
            "PROCESSED" -> "Estado: procesado."
            "FAILED" -> "Estado: fallido."
            "ERROR" -> "Estado: fallido."
            null -> ""
            else -> "Estado: pendiente."
        }
        message.text = if (statusMsg.isBlank()) msg else "$msg\n\n$statusMsg"

        btn.text = if (success) "Confirmar" else "Cerrar"
        btn.setOnClickListener {
            dialog.dismiss()
            onConfirm?.invoke()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        dialog.window?.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private suspend fun awaitFinalEventStatus(payload: ScanSendResult?): String {
        val initial = payload?.status?.uppercase(Locale.ROOT) ?: "PENDING"
        val eventId = payload?.eventId ?: return initial
        if (initial == "PROCESSED" || initial == "ERROR" || initial == "FAILED") return initial

        repeat(4) {
            delay(700)
            val res = runCatching { NetworkModule.api.listEvents(limit = 30, offset = 0) }.getOrNull()
            val status = res?.body()?.items?.firstOrNull { it.id == eventId }?.eventStatus?.uppercase(Locale.ROOT)
            if (status == "PROCESSED" || status == "ERROR" || status == "FAILED") {
                return status
            }
        }
        return initial
    }

    private fun goBackToScan() {
        val intent = Intent(this, ScanActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.etLocation.windowToken, 0)
    }
}
