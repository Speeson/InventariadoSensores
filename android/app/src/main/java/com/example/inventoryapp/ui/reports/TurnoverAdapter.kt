package com.example.inventoryapp.ui.reports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R

class TurnoverAdapter(
    private var items: List<TurnoverRow>,
    private val locationFilter: String?
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
        holder.tvProduct.text = "Producto: ${item.name}"
        holder.tvSku.text = "Sku: ${item.sku}"
        holder.tvOuts.text = "Ventas totales: ${item.outs}"
        holder.tvAvg.text = "Stock medio - Rotacion: ${String.format("%.2f", item.stockAverage)} / $turnoverText"
        holder.tvInitFinal.text = "Inicial: ${String.format("%.2f", item.stockInitial)} - Final: ${String.format("%.2f", item.stockFinal)}"
        if (!locationFilter.isNullOrBlank()) {
            holder.tvLocation.visibility = View.VISIBLE
            holder.tvLocation.text = "Ubicacion: $locationFilter"
        } else {
            holder.tvLocation.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProduct: TextView = view.findViewById(R.id.tvTurnProduct)
        val tvSku: TextView = view.findViewById(R.id.tvTurnSku)
        val tvOuts: TextView = view.findViewById(R.id.tvTurnOuts)
        val tvAvg: TextView = view.findViewById(R.id.tvTurnAvg)
        val tvInitFinal: TextView = view.findViewById(R.id.tvTurnInitFinal)
        val tvLocation: TextView = view.findViewById(R.id.tvTurnLocation)
    }
}
