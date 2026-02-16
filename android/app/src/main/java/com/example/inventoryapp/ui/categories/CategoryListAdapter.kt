package com.example.inventoryapp.ui.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.databinding.ItemCategoryCardBinding

class CategoryListAdapter(
    private val onClick: (CategoryResponseDto) -> Unit
) : RecyclerView.Adapter<CategoryListAdapter.VH>() {

    private val items = mutableListOf<CategoryResponseDto>()

    inner class VH(val binding: ItemCategoryCardBinding) : RecyclerView.ViewHolder(binding.root) {
        val nameColor = binding.tvName.currentTextColor
        val metaColor = binding.tvMeta.currentTextColor
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCategoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val isPendingDelete = c.updatedAt == "offline_delete"
        val isOffline = c.id < 0 || c.createdAt == "offline"
        val name = if (isOffline) "${c.name} (offline)" else c.name
        val idLabel = if (isOffline) "offline" else c.id.toString()
        holder.binding.tvName.text = name
        holder.binding.tvMeta.text = "ID: $idLabel"
        holder.binding.ivWarning.visibility =
            if (isOffline || isPendingDelete) android.view.View.VISIBLE else android.view.View.GONE
        val pendingTooltip = if (isPendingDelete) {
            "Pendiente de eliminar en sincronizacion offline"
        } else {
            "Guardado en modo offline, pendiente de sincronizacion"
        }
        holder.binding.ivWarning.setImageResource(
            if (isPendingDelete) R.drawable.ic_close_red else R.drawable.sync
        )
        TooltipCompat.setTooltipText(holder.binding.ivWarning, if (isOffline || isPendingDelete) pendingTooltip else null)
        holder.binding.ivWarning.contentDescription = if (isOffline || isPendingDelete) pendingTooltip else "Pendiente"
        val offlineColor = ContextCompat.getColor(holder.itemView.context, R.color.offline_text)
        if (isOffline || isPendingDelete) {
            holder.binding.tvName.setTextColor(offlineColor)
            holder.binding.tvMeta.setTextColor(offlineColor)
        } else {
            holder.binding.tvName.setTextColor(holder.nameColor)
            holder.binding.tvMeta.setTextColor(holder.metaColor)
        }
        holder.binding.root.setOnClickListener { onClick(c) }
    }

    fun submit(newItems: List<CategoryResponseDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
