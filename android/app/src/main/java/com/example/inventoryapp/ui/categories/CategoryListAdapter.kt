package com.example.inventoryapp.ui.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.databinding.ItemCategoryCardBinding

class CategoryListAdapter(
    private val onClick: (CategoryResponseDto) -> Unit
) : RecyclerView.Adapter<CategoryListAdapter.VH>() {

    private val items = mutableListOf<CategoryResponseDto>()

    inner class VH(val binding: ItemCategoryCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCategoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val isOffline = c.id < 0 || c.createdAt == "offline"
        val name = if (isOffline) "${c.name} (offline)" else c.name
        val idLabel = if (isOffline) "offline" else c.id.toString()
        holder.binding.tvName.text = name
        holder.binding.tvMeta.text = "ID: $idLabel"
        holder.binding.root.setOnClickListener { onClick(c) }
    }

    fun submit(newItems: List<CategoryResponseDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
