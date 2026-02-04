package com.example.inventoryapp.ui.stock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.remote.model.StockResponseDto
import com.example.inventoryapp.databinding.ItemStockCardBinding

class StockListAdapter(
    private val onClick: (StockResponseDto) -> Unit
) : RecyclerView.Adapter<StockListAdapter.VH>() {

    private val items = mutableListOf<StockResponseDto>()

    inner class VH(val binding: ItemStockCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStockCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        val isOffline = s.id < 0 || s.createdAt == "offline"
        val titleId = if (isOffline) "offline" else s.id.toString()
        val titleSuffix = if (isOffline) " (offline)" else ""
        holder.binding.tvTitle.text = "Stock #$titleId$titleSuffix  •  Prod ${s.productId}"
        holder.binding.tvLocation.text = "Ubicacion: ${s.location}"
        holder.binding.tvMeta.text = "Cantidad: ${s.quantity}"
        holder.binding.root.setOnClickListener { onClick(s) }
    }

    fun submit(newItems: List<StockResponseDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
