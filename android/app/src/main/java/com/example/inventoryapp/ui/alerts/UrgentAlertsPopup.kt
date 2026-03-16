package com.example.inventoryapp.ui.alerts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.PopupAlertDismissStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.AlertResponseDto
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.data.remote.model.AlertTypeDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import kotlinx.coroutines.launch

object UrgentAlertsPopup {

    private data class PopupItem(
        val stableId: String,
        val title: String,
        val subtitle: String,
        val timestamp: String,
        val iconRes: Int,
        var expanded: Boolean = false,
    )

    fun show(
        activity: AppCompatActivity,
        onDismiss: (() -> Unit)? = null,
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_urgent_alerts, null)
        val recycler = view.findViewById<RecyclerView>(R.id.rvUrgentAlerts)
        val empty = view.findViewById<TextView>(R.id.tvUrgentAlertsEmpty)
        val close = view.findViewById<ImageButton>(R.id.btnUrgentAlertsClose)
        val clearAll = view.findViewById<ImageButton>(R.id.btnUrgentAlertsClearAll)
        val title = view.findViewById<TextView>(R.id.tvUrgentAlertsTitle)

        val adapter = PopupAlertAdapter()
        val dismissedStore = PopupAlertDismissStore(activity)

        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = adapter

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        fun render(items: List<PopupItem>) {
            adapter.submit(items)
            empty.isVisible = items.isEmpty()
            title.text = "Alertas prioritarias (${items.size})"
            if (items.isEmpty()) {
                empty.text = "No hay alertas prioritarias ahora mismo"
            }
        }

        suspend fun buildItems(): List<PopupItem> {
            val dismissed = dismissedStore.list()
            val result = mutableListOf<PopupItem>()

            val alertsResponse = NetworkModule.api.listAlerts(
                status = AlertStatusDto.PENDING,
                limit = 50,
                offset = 0
            )
            if (alertsResponse.isSuccessful && alertsResponse.body() != null) {
                val alerts = alertsResponse.body()!!.items
                val visibleAlerts = alerts.filter { isAlertVisibleForUser(activity, it) }
                val enriched = enrichAlerts(visibleAlerts)
                enriched
                    .filterNot { dismissed.contains("alert:${it.alert.id}") }
                    .forEach { row ->
                        val alert = row.alert
                        val fallbackProduct = alert.stockId?.let { "Producto $it" } ?: "Importacion"
                        val productLabel = row.productName ?: fallbackProduct
                        val locationLabel = row.location ?: "N/D"
                        result.add(
                            PopupItem(
                                stableId = "alert:${alert.id}",
                                title = titleFor(alert),
                                subtitle = metaFor(alert, productLabel, locationLabel),
                                timestamp = alert.createdAt,
                                iconRes = iconFor(alert),
                            )
                        )
                    }
            }

            val eventsResponse = NetworkModule.api.listEvents(limit = 100, offset = 0)
            if (eventsResponse.isSuccessful && eventsResponse.body() != null) {
                eventsResponse.body()!!.items
                    .filter { isFailedEvent(it) }
                    .filterNot { dismissed.contains("event:${it.id}") }
                    .forEach { event ->
                        result.add(
                            PopupItem(
                                stableId = "event:${event.id}",
                                title = "Evento fallido: ${event.eventType}",
                                subtitle = buildEventMeta(event),
                                timestamp = event.createdAt,
                                iconRes = R.drawable.ic_error_red,
                            )
                        )
                    }
            }

            return result.sortedByDescending { it.timestamp }
        }

        fun loadItems() {
            activity.lifecycleScope.launch {
                try {
                    render(buildItems())
                } catch (e: Exception) {
                    render(emptyList())
                    empty.isVisible = true
                    empty.text = e.message ?: "No se pudieron cargar las alertas prioritarias"
                }
            }
        }

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = if (position != RecyclerView.NO_POSITION) adapter.itemAt(position) else null
                if (item != null) {
                    dismissedStore.add(item.stableId)
                    adapter.remove(item.stableId)
                    empty.isVisible = adapter.itemCount == 0
                    if (adapter.itemCount == 0) {
                        empty.text = "No hay alertas prioritarias ahora mismo"
                    }
                    title.text = "Alertas prioritarias (${adapter.itemCount})"
                } else {
                    loadItems()
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recycler)

        clearAll.setOnClickListener {
            dismissedStore.addAll(adapter.currentIds())
            render(emptyList())
        }

        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { onDismiss?.invoke() }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        loadItems()
    }

    private fun isFailedEvent(event: EventResponseDto): Boolean {
        val status = event.eventStatus?.uppercase() ?: if (event.processed) "PROCESSED" else "PENDING"
        return status == "ERROR" || status == "FAILED"
    }

    private fun buildEventMeta(event: EventResponseDto): String {
        return "ID ${event.id} - Producto ${event.productId} - delta ${event.delta} - ${event.source}"
    }

    private fun isAlertVisibleForUser(activity: AppCompatActivity, alert: AlertResponseDto): Boolean {
        val prefs = activity.getSharedPreferences("ui_prefs", 0)
        val role = prefs.getString("cached_role", null) ?: return true
        if (!role.equals("USER", ignoreCase = true)) return true
        return alert.alertType == AlertTypeDto.LOW_STOCK || alert.alertType == AlertTypeDto.OUT_OF_STOCK
    }

    private suspend fun enrichAlerts(items: List<AlertResponseDto>): List<AlertRowUi> {
        return items.map { alert ->
            var productName: String? = null
            var location: String? = null
            try {
                val stockId = alert.stockId
                if (stockId != null) {
                    val stockRes = NetworkModule.api.getStock(stockId)
                    if (stockRes.isSuccessful && stockRes.body() != null) {
                        val stock = stockRes.body()!!
                        location = stock.location
                        val productRes = NetworkModule.api.getProduct(stock.productId)
                        if (productRes.isSuccessful && productRes.body() != null) {
                            productName = productRes.body()!!.name
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep fallback labels if lookup fails.
            }
            AlertRowUi(alert = alert, productName = productName, location = location)
        }
    }

    private fun titleFor(alert: AlertResponseDto): String {
        val type = when (alert.alertType) {
            AlertTypeDto.LOW_STOCK -> "Stock bajo"
            AlertTypeDto.OUT_OF_STOCK -> "Stock agotado"
            AlertTypeDto.LARGE_MOVEMENT -> "Movimiento grande"
            AlertTypeDto.TRANSFER_COMPLETE -> "Transferencia completa"
            AlertTypeDto.IMPORT_ISSUES -> "Importacion con errores"
        }
        return "Alerta #${alert.id} - $type"
    }

    private fun metaFor(alert: AlertResponseDto, productLabel: String, locationLabel: String): String {
        return when (alert.alertType) {
            AlertTypeDto.TRANSFER_COMPLETE ->
                "Producto: $productLabel - Loc $locationLabel - Cantidad ${alert.quantity}"
            AlertTypeDto.LARGE_MOVEMENT ->
                "Producto: $productLabel - Movimiento ${alert.quantity} - Loc $locationLabel"
            AlertTypeDto.IMPORT_ISSUES ->
                "Importacion con incidencias - Revisar detalle"
            else ->
                "Producto: $productLabel - Qty ${alert.quantity} / Min ${alert.minQuantity} - Loc $locationLabel"
        }
    }

    private fun iconFor(alert: AlertResponseDto): Int {
        return when (alert.alertType) {
            AlertTypeDto.OUT_OF_STOCK -> R.drawable.alert_red
            AlertTypeDto.LOW_STOCK -> R.drawable.alert_yellow
            AlertTypeDto.TRANSFER_COMPLETE -> R.drawable.alert_green
            AlertTypeDto.LARGE_MOVEMENT -> R.drawable.alert_violet
            AlertTypeDto.IMPORT_ISSUES -> R.drawable.alert_blue
        }
    }

    private class PopupAlertAdapter : RecyclerView.Adapter<PopupAlertAdapter.ViewHolder>() {
        private val items = mutableListOf<PopupItem>()

        fun submit(newItems: List<PopupItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun remove(stableId: String) {
            val index = items.indexOfFirst { it.stableId == stableId }
            if (index >= 0) {
                items.removeAt(index)
                notifyItemRemoved(index)
            }
        }

        fun itemAt(position: Int): PopupItem? = items.getOrNull(position)

        fun currentIds(): List<String> = items.map { it.stableId }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_urgent_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageResource(item.iconRes)
            holder.title.text = item.title
            holder.subtitle.text = item.subtitle
            holder.timestamp.text = item.timestamp
            holder.details.visibility = if (item.expanded) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                item.expanded = !item.expanded
                val currentPosition = holder.adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(currentPosition)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivUrgentItemIcon)
            val title: TextView = view.findViewById(R.id.tvUrgentItemTitle)
            val details: LinearLayout = view.findViewById(R.id.layoutUrgentItemDetails)
            val subtitle: TextView = view.findViewById(R.id.tvUrgentItemSubtitle)
            val timestamp: TextView = view.findViewById(R.id.tvUrgentItemTimestamp)
        }
    }
}
