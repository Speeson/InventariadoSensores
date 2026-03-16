package com.example.inventoryapp.ui.alerts

import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.PopupAlertDismissStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.AlertResponseDto
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.data.remote.model.AlertTypeDto
import com.example.inventoryapp.data.remote.model.EventResponseDto

object UrgentAlertsRepository {

    data class UrgentAlertItem(
        val stableId: String,
        val title: String,
        val subtitle: String,
        val timestamp: String,
        val iconRes: Int,
    )

    suspend fun load(activity: AppCompatActivity): List<UrgentAlertItem> {
        val dismissed = PopupAlertDismissStore(activity).list()
        val result = mutableListOf<UrgentAlertItem>()

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
                        UrgentAlertItem(
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
                        UrgentAlertItem(
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
}
