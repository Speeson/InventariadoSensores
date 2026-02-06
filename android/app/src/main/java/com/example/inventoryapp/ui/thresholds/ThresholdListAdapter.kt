package com.example.inventoryapp.ui.thresholds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.remote.model.ThresholdResponseDto
import com.example.inventoryapp.databinding.ItemThresholdCardBinding

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
        val loc = t.location ?: "-"
        val productLabel = row.productName ?: "Producto ${t.productId}"
        val isOffline = t.id < 0 || t.createdAt == "offline"
        val idLabel = if (isOffline) "offline" else t.id.toString()
        val titleSuffix = if (isOffline) " (offline)" else ""
        holder.binding.tvTitle.text = "Producto: $productLabel$titleSuffix"
        holder.binding.tvMeta.text = "Umbral: ${t.minQuantity}  •  Loc: $loc  •  ID $idLabel"
        holder.binding.ivWarning.visibility =
            if (isOffline) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.root.setOnClickListener { onClick(t) }
    }

    fun submit(newItems: List<ThresholdRowUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
