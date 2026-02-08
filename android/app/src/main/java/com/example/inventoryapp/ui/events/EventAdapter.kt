package com.example.inventoryapp.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R

class EventAdapter(
    private var items: List<EventRowUi>,
    private val onPendingClick: (EventRowUi) -> Unit
) : RecyclerView.Adapter<EventAdapter.ViewHolder>() {

    fun submit(list: List<EventRowUi>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = "Evento"
        holder.tvIdBadge.text = "${item.id}"
        val productLabel = item.productName ?: "Producto ${item.productId}"
        holder.tvMeta.text = "${item.eventType} | $productLabel | delta=${item.delta} | src=${item.source}"
        val status = item.status
        holder.tvStatus.text = status

        val colorRes = when (status.uppercase()) {
            "PROCESSED" -> android.R.color.holo_green_dark
            "PENDING" -> android.R.color.holo_orange_dark
            "ERROR", "FAILED" -> android.R.color.holo_red_dark
            else -> android.R.color.darker_gray
        }
        val color = ContextCompat.getColor(holder.itemView.context, colorRes)
        holder.tvStatus.setTextColor(color)
        holder.ivIcon.setColorFilter(color)

        holder.tvDate.text = item.createdAt
        holder.ivPending.visibility = if (item.isPending) View.VISIBLE else View.GONE
        holder.ivWarning.visibility = if (item.isPending) View.VISIBLE else View.GONE
        val offlineColor = ContextCompat.getColor(holder.itemView.context, R.color.offline_text)
        if (item.isPending) {
            holder.tvTitle.setTextColor(offlineColor)
            holder.tvIdBadge.setTextColor(offlineColor)
            holder.tvMeta.setTextColor(offlineColor)
            holder.tvDate.setTextColor(offlineColor)
        } else {
            holder.tvTitle.setTextColor(holder.titleColor)
            holder.tvIdBadge.setTextColor(holder.idBadgeColor)
            holder.tvMeta.setTextColor(holder.metaColor)
            holder.tvDate.setTextColor(holder.dateColor)
        }
        holder.ivPending.setOnClickListener {
            if (item.isPending) onPendingClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvIdBadge: TextView = view.findViewById(R.id.tvEventIdBadge)
        val tvMeta: TextView = view.findViewById(R.id.tvEventMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvEventStatus)
        val tvDate: TextView = view.findViewById(R.id.tvEventDate)
        val ivPending: ImageView = view.findViewById(R.id.ivEventPending)
        val ivWarning: ImageView = view.findViewById(R.id.ivWarning)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val titleColor: Int = tvTitle.currentTextColor
        val idBadgeColor: Int = tvIdBadge.currentTextColor
        val metaColor: Int = tvMeta.currentTextColor
        val dateColor: Int = tvDate.currentTextColor
    }
}
