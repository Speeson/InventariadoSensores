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
        holder.tvTitle.text = "${item.sku} - ${item.name}"
        holder.tvMeta.text = "Cantidad=${item.quantity} | ${item.fromLocation} -> ${item.toLocation}"
        holder.tvId.text = "ID ${item.productId} | ${item.createdAt}"
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvRotationTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvRotationMeta)
        val tvId: TextView = view.findViewById(R.id.tvRotationId)
    }
}
