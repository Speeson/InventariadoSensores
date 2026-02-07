package com.example.inventoryapp.ui.reports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.TopConsumedItemDto

class TopConsumedAdapter(
    private var items: List<TopConsumedItemDto>,
    private val locationFilter: String?
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
        holder.tvProduct.text = "Producto: ${item.name}"
        holder.tvSku.text = "Sku: ${item.sku}"
        holder.tvTotal.text = "Ventas totales: ${item.totalOut}"
        if (!locationFilter.isNullOrBlank()) {
            holder.tvLocation.visibility = View.VISIBLE
            holder.tvLocation.text = "Ubicacion: $locationFilter"
        } else {
            holder.tvLocation.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProduct: TextView = view.findViewById(R.id.tvTopProduct)
        val tvSku: TextView = view.findViewById(R.id.tvTopSku)
        val tvTotal: TextView = view.findViewById(R.id.tvTopTotal)
        val tvLocation: TextView = view.findViewById(R.id.tvTopLocation)
    }
}
