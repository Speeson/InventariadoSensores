package com.example.inventoryapp.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R

class EventAdapter(
    private var items: List<EventRowUi>
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
        val status = item.status.uppercase()
        holder.tvTitle.text = when (status) {
            "PROCESSED" -> "${item.eventType} - PROCESSED"
            "PENDING" -> "${item.eventType} - PENDING"
            "ERROR", "FAILED" -> "${item.eventType} - ERROR"
            else -> "${item.eventType} - $status"
        }
        holder.tvIdValue.text = "${item.id}"
        val productLabel = item.productName ?: "Producto ${item.productId}"
        holder.tvMeta.text = "${item.eventType} | $productLabel | delta=${item.delta} | src=${item.source}"
        holder.tvStatus.text = status

        val colorRes = when (status) {
            "PROCESSED" -> android.R.color.holo_green_dark
            "PENDING" -> android.R.color.holo_orange_dark
            "ERROR", "FAILED" -> android.R.color.holo_red_dark
            else -> android.R.color.darker_gray
        }
        val color = ContextCompat.getColor(holder.itemView.context, colorRes)
        holder.tvStatus.visibility = View.GONE
        holder.ivIcon.setColorFilter(color)
        holder.tvTitle.setTextColor(color)

        holder.tvDate.text = item.createdAt
        holder.ivPending.visibility = if (item.isPending) View.VISIBLE else View.GONE
        holder.ivPending.setImageResource(R.drawable.sync)
        val pendingTooltip = item.pendingMessage ?: "Guardado en modo offline, pendiente de sincronización"
        TooltipCompat.setTooltipText(holder.ivPending, if (item.isPending) pendingTooltip else null)
        holder.ivPending.contentDescription = if (item.isPending) pendingTooltip else "Pendiente"
        val offlineColor = ContextCompat.getColor(holder.itemView.context, R.color.offline_text)
        if (item.isPending) {
            holder.tvIdLabel.setTextColor(offlineColor)
            holder.tvIdValue.setTextColor(offlineColor)
            holder.tvMeta.setTextColor(offlineColor)
            holder.tvDate.setTextColor(offlineColor)
        } else {
            holder.tvIdLabel.setTextColor(holder.idLabelColor)
            holder.tvIdValue.setTextColor(holder.idValueColor)
            holder.tvMeta.setTextColor(holder.metaColor)
            holder.tvDate.setTextColor(holder.dateColor)
        }
        holder.ivPending.setOnClickListener(null)

        val params = holder.itemView.layoutParams as? RecyclerView.LayoutParams
        if (params != null) {
            val bottom = if (position == items.lastIndex) 0 else holder.defaultBottomMargin
            if (params.bottomMargin != bottom) {
                params.bottomMargin = bottom
                holder.itemView.layoutParams = params
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvIdLabel: TextView = view.findViewById(R.id.tvEventIdLabel)
        val tvIdValue: TextView = view.findViewById(R.id.tvEventIdValue)
        val tvMeta: TextView = view.findViewById(R.id.tvEventMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvEventStatus)
        val tvDate: TextView = view.findViewById(R.id.tvEventDate)
        val ivPending: ImageView = view.findViewById(R.id.ivEventPending)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val titleColor: Int = tvTitle.currentTextColor
        val idLabelColor: Int = tvIdLabel.currentTextColor
        val idValueColor: Int = tvIdValue.currentTextColor
        val metaColor: Int = tvMeta.currentTextColor
        val dateColor: Int = tvDate.currentTextColor
        val defaultBottomMargin: Int = (10f * itemView.resources.displayMetrics.density).toInt()
    }
}
