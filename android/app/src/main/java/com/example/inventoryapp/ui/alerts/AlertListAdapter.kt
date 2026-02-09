package com.example.inventoryapp.ui.alerts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.AlertResponseDto
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.data.remote.model.AlertTypeDto

data class AlertRowUi(
    val alert: AlertResponseDto,
    val productName: String?,
    val location: String?
)

class AlertListAdapter(
    private var items: List<AlertRowUi>,
    private val onClick: (AlertRowUi) -> Unit
) : RecyclerView.Adapter<AlertListAdapter.ViewHolder>() {

    fun submit(list: List<AlertRowUi>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alert_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val alert = item.alert
        holder.tvTitle.text = titleFor(alert)
        val fallbackProduct = alert.stockId?.let { "Producto $it" } ?: "Importación"
        val productLabel = item.productName ?: fallbackProduct
        val locationLabel = item.location ?: "N/D"
        holder.tvMeta.text = metaFor(alert, productLabel, locationLabel)
        holder.tvDate.text = alert.createdAt
        holder.tvStatus.text = statusLabel(alert.alertStatus)

        val statusColorRes = when (alert.alertStatus) {
            AlertStatusDto.PENDING -> android.R.color.holo_orange_dark
            AlertStatusDto.ACK -> android.R.color.holo_blue_light
            AlertStatusDto.RESOLVED -> android.R.color.holo_green_dark
        }
        val statusColor = ContextCompat.getColor(holder.itemView.context, statusColorRes)
        holder.tvStatus.setTextColor(statusColor)

        val iconRes = if (alert.alertStatus == AlertStatusDto.ACK) {
            R.drawable.correct
        } else {
            iconFor(alert)
        }
        holder.ivIcon.setImageResource(iconRes)
        holder.ivIcon.clearColorFilter()

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    private fun statusLabel(status: AlertStatusDto): String {
        return when (status) {
            AlertStatusDto.PENDING -> "Pendiente"
            AlertStatusDto.ACK -> "Vista"
            AlertStatusDto.RESOLVED -> "Resuelta"
        }
    }

    private fun titleFor(alert: AlertResponseDto): String {
        val type = when (alert.alertType) {
            AlertTypeDto.LOW_STOCK -> "Stock bajo"
            AlertTypeDto.OUT_OF_STOCK -> "Stock agotado"
            AlertTypeDto.LARGE_MOVEMENT -> "Movimiento grande"
            AlertTypeDto.TRANSFER_COMPLETE -> "Transferencia completa"
            AlertTypeDto.IMPORT_ISSUES -> "Importación con errores"
        }
        return "Alerta #${alert.id} - $type"
    }

    private fun metaFor(alert: AlertResponseDto, productLabel: String, locationLabel: String): String {
        return when (alert.alertType) {
            AlertTypeDto.TRANSFER_COMPLETE ->
                "Producto: $productLabel · Loc $locationLabel · Cantidad ${alert.quantity}"
            AlertTypeDto.LARGE_MOVEMENT ->
                "Producto: $productLabel · Movimiento ${alert.quantity} · Loc $locationLabel"
            AlertTypeDto.IMPORT_ISSUES ->
                "Importación con incidencias · Revisar en CSV"
            else ->
                "Producto: $productLabel · Qty ${alert.quantity} / Min ${alert.minQuantity} · Loc $locationLabel"
        }
    }

    private fun typeColor(alert: AlertResponseDto, holder: ViewHolder): Int {
        val ctx = holder.itemView.context
        return when (alert.alertType) {
            AlertTypeDto.OUT_OF_STOCK -> ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
            AlertTypeDto.LOW_STOCK -> ContextCompat.getColor(ctx, android.R.color.holo_orange_dark)
            AlertTypeDto.TRANSFER_COMPLETE -> ContextCompat.getColor(ctx, android.R.color.holo_green_dark)
            AlertTypeDto.LARGE_MOVEMENT -> ContextCompat.getColor(ctx, android.R.color.holo_purple)
            AlertTypeDto.IMPORT_ISSUES -> ContextCompat.getColor(ctx, android.R.color.holo_blue_dark)
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

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }
}
