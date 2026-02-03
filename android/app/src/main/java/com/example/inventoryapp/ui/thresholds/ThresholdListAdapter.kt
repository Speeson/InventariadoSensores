package com.example.inventoryapp.ui.thresholds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.remote.model.ThresholdResponseDto
import com.example.inventoryapp.databinding.ItemThresholdCardBinding

class ThresholdListAdapter(
    private val onClick: (ThresholdResponseDto) -> Unit
) : RecyclerView.Adapter<ThresholdListAdapter.VH>() {

    private val items = mutableListOf<ThresholdResponseDto>()

    inner class VH(val binding: ItemThresholdCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemThresholdCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        val loc = t.location ?: "-"
        holder.binding.tvTitle.text = "Prod ${t.productId}  •  Min ${t.minQuantity}"
        holder.binding.tvMeta.text = "Loc: ${loc}  •  ID ${t.id}"
        holder.binding.root.setOnClickListener { onClick(t) }
    }

    fun submit(newItems: List<ThresholdResponseDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
