package com.example.inventoryapp.ui.products

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.databinding.ItemProductCardBinding

class ProductListAdapter(
    private val onClick: (ProductResponseDto) -> Unit
) : RecyclerView.Adapter<ProductListAdapter.VH>() {

    private val items = mutableListOf<ProductResponseDto>()

    inner class VH(val binding: ItemProductCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.binding.tvName.text = p.name
        holder.binding.tvSku.text = "SKU: ${p.sku}"
        holder.binding.tvBarcode.text = "Barcode: ${p.barcode ?: "-"}"
        holder.binding.tvMeta.text = "ID: ${p.id}  •  Cat: ${p.categoryId}"
        holder.binding.root.setOnClickListener { onClick(p) }
    }

    fun submit(newItems: List<ProductResponseDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
