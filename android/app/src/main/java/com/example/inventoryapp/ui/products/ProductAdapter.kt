package com.example.inventoryapp.ui.products

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.databinding.ItemProductBinding
import com.example.inventoryapp.domain.model.Product

class ProductAdapter(
    private val items: List<Product>,
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    inner class VH(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.binding.tvName.text = p.name
        holder.binding.tvSku.text = "SKU: ${p.sku} | ${p.category}"
        holder.binding.tvStock.text = "Stock: ${p.stock}"
        holder.binding.root.setOnClickListener { onClick(p) }
    }
}
