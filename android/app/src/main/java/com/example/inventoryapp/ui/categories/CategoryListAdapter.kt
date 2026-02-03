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
        holder.binding.tvName.text = c.name
        holder.binding.tvMeta.text = "ID: ${c.id}"
        holder.binding.root.setOnClickListener { onClick(c) }
    }

    fun submit(newItems: List<CategoryResponseDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
