package com.example.inventoryapp.ui.rotation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R

class RotationAdapter(
    private var items: List<RotationRow>
) : RecyclerView.Adapter<RotationAdapter.ViewHolder>() {

    fun submit(list: List<RotationRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rotation_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val turnoverText = item.turnover?.let { String.format("%.2f", it) } ?: "N/A"
        holder.tvTitle.text = "${item.sku} - ${item.name}"
        holder.tvMeta.text = "OUT=${item.outs} | AVG=${String.format("%.2f", item.stockAverage)} | TURN=$turnoverText"
        holder.tvId.text = "ID ${item.productId} | INIT=${String.format("%.2f", item.stockInitial)} | FINAL=${String.format("%.2f", item.stockFinal)}"
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvRotationTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvRotationMeta)
        val tvId: TextView = view.findViewById(R.id.tvRotationId)
    }
}
