package com.example.inventoryapp.ui.thresholds

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.remote.model.ThresholdResponseDto
import com.example.inventoryapp.databinding.ItemThresholdCardBinding
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R

data class ThresholdRowUi(
    val threshold: ThresholdResponseDto,
    val productName: String?
)

class ThresholdListAdapter(
    private val onClick: (ThresholdResponseDto) -> Unit
) : RecyclerView.Adapter<ThresholdListAdapter.VH>() {

    private val items = mutableListOf<ThresholdRowUi>()

    inner class VH(val binding: ItemThresholdCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemThresholdCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val t = row.threshold
        val productLabel = row.productName ?: "Producto ${t.productId}"
        val isOffline = t.id < 0 || t.createdAt == "offline"
        val idLabel = if (isOffline) "offline" else t.id.toString()
        val titleSuffix = if (isOffline) " (offline)" else ""

        holder.binding.tvTitle.text = "Producto: $productLabel$titleSuffix"
        holder.binding.tvLocation.text = "Ubicacion: ${t.location ?: "-"}"
        holder.binding.tvThreshold.text = "Umbral: ${t.minQuantity}"
        holder.binding.tvThresholdId.text = "ID $idLabel"

        GradientIconUtil.applyGradient(holder.binding.ivThresholdIcon, R.drawable.threshold)

        holder.binding.root.setOnClickListener { onClick(t) }
    }

    fun submit(newItems: List<ThresholdRowUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
