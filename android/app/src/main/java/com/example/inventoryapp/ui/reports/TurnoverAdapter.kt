package com.example.inventoryapp.ui.reports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R

class TurnoverAdapter(
    private var items: List<TurnoverRow>
) : RecyclerView.Adapter<TurnoverAdapter.ViewHolder>() {

    fun submit(list: List<TurnoverRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_turnover_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val turnoverText = item.turnover?.let { String.format("%.2f", it) } ?: "N/A"
        holder.tvTitle.text = "${item.sku} - ${item.name}"
        holder.tvMeta.text =
            "Salidas=${item.outs} | Stock medio=${String.format("%.2f", item.stockAverage)} | Rotacion=$turnoverText"
        holder.tvId.text =
            "ID ${item.productId} | Inicial=${String.format("%.2f", item.stockInitial)} | Final=${String.format("%.2f", item.stockFinal)}"
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTurnTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvTurnMeta)
        val tvId: TextView = view.findViewById(R.id.tvTurnId)
    }
}
