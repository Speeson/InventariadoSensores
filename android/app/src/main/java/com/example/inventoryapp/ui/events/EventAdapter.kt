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
        holder.tvTitle.text = "Evento #${item.id}"
        holder.tvMeta.text = "${item.eventType} | prod=${item.productId} | Δ=${item.delta}"
        val status = item.status
        holder.tvStatus.text = status

        val colorRes = when (status.uppercase()) {
            "PROCESSED" -> android.R.color.holo_green_dark
            "PENDING" -> android.R.color.holo_orange_dark
            "ERROR" -> android.R.color.holo_red_dark
            else -> android.R.color.darker_gray
        }
        holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))

        holder.tvDate.text = item.createdAt
        holder.ivPending.visibility = if (item.isPending) View.VISIBLE else View.GONE
        holder.ivPending.setOnClickListener {
            if (item.isPending) onPendingClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvEventMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvEventStatus)
        val tvDate: TextView = view.findViewById(R.id.tvEventDate)
        val ivPending: ImageView = view.findViewById(R.id.ivEventPending)
    }
}
