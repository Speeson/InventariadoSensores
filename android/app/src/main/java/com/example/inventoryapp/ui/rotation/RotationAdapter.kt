package com.example.inventoryapp.ui.rotation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.databinding.ItemRotationRowBinding

class RotationAdapter : RecyclerView.Adapter<RotationAdapter.VH>() {

    private val items = mutableListOf<RotationRow>()

    fun submitList(newItems: List<RotationRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemRotationRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRotationRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.binding.tvName.text = it.productName
        holder.binding.tvIn.text = it.inQty.toString()
        holder.binding.tvOut.text = it.outQty.toString()
        holder.binding.tvNet.text = it.net.toString()
        holder.binding.tvCount.text = it.eventsCount.toString()
        holder.binding.tvLast.text = it.lastDate
    }

    override fun getItemCount(): Int = items.size
}
