package com.example.inventoryapp.ui.reports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.TopConsumedItemDto

class TopConsumedAdapter(
    private var items: List<TopConsumedItemDto>
) : RecyclerView.Adapter<TopConsumedAdapter.ViewHolder>() {

    fun submit(list: List<TopConsumedItemDto>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_consumed_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = "${item.sku} - ${item.name}"
        holder.tvMeta.text = "OUT=${item.totalOut}"
        holder.tvId.text = "ID ${item.productId}"
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTopTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvTopMeta)
        val tvId: TextView = view.findViewById(R.id.tvTopId)
    }
}
