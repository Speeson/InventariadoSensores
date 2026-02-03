package com.example.inventoryapp.ui.alerts

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.PendingRequest

class OfflinePendingAdapter(
    private var items: List<PendingRequest>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<OfflinePendingAdapter.ViewHolder>() {

    fun submit(list: List<PendingRequest>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_offline_pending_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val whenStr = DateFormat.format("dd/MM/yyyy HH:mm:ss", item.createdAt).toString()
        holder.tvTitle.text = "Pendiente: ${item.type}"
        holder.tvMeta.text = "Creado: $whenStr"
        holder.tvMessage.text = "En cola para sincronización"
        holder.itemView.setOnClickListener { onClick(position) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
    }
}
