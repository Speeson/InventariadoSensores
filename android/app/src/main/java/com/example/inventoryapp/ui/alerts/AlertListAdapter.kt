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
        holder.tvTitle.text = "Alerta #${alert.id} - Stock bajo"
        val productLabel = item.productName ?: "Producto ${alert.stockId}"
        val locationLabel = item.location ?: "N/D"
        holder.tvMeta.text = "Producto: $productLabel · Qty ${alert.quantity} / Min ${alert.minQuantity} · Loc $locationLabel"
        holder.tvDate.text = alert.createdAt
        holder.tvStatus.text = statusLabel(alert.alertStatus)

        val colorRes = when (alert.alertStatus) {
            AlertStatusDto.PENDING -> android.R.color.holo_orange_dark
            AlertStatusDto.ACK -> android.R.color.holo_blue_dark
            AlertStatusDto.RESOLVED -> android.R.color.holo_green_dark
        }
        val color = ContextCompat.getColor(holder.itemView.context, colorRes)
        holder.tvStatus.setTextColor(color)
        holder.ivIcon.setColorFilter(color)

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

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }
}
