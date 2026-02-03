package com.example.inventoryapp.ui.alerts

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.SystemAlert

class SystemAlertAdapter(
    private var items: List<SystemAlert>,
    private val onClick: (SystemAlert) -> Unit
) : RecyclerView.Adapter<SystemAlertAdapter.ViewHolder>() {

    fun submit(list: List<SystemAlert>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_system_alert_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val whenStr = DateFormat.format("dd/MM/yyyy HH:mm:ss", item.createdAt).toString()
        holder.tvTitle.text = item.title
        holder.tvMessage.text = item.message
        holder.tvMeta.text = "${item.type} · $whenStr"
        holder.itemView.alpha = if (item.seen) 0.7f else 1.0f
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
    }
}
