package com.example.inventoryapp.ui.stock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.StockResponseDto
import com.example.inventoryapp.databinding.ItemStockCardBinding

class StockListAdapter(
    private val onClick: (StockResponseDto) -> Unit
) : RecyclerView.Adapter<StockListAdapter.VH>() {

    private val items = mutableListOf<StockResponseDto>()
    private var productNameById: Map<Int, String> = emptyMap()

    inner class VH(val binding: ItemStockCardBinding) : RecyclerView.ViewHolder(binding.root) {
        val idColor = binding.tvStockId.currentTextColor
        val titleColor = binding.tvTitle.currentTextColor
        val locationColor = binding.tvLocation.currentTextColor
        val metaColor = binding.tvMeta.currentTextColor
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStockCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        val isPendingDelete = s.updatedAt == "offline_delete"
        val isOffline = s.id < 0 || s.createdAt == "offline"
        val titleId = if (isOffline) "offline" else s.id.toString()
        val productName = productNameById[s.productId] ?: "Producto"
        holder.binding.tvStockId.text = "ID\n$titleId"
        val nameSuffix = if (isOffline) " (offline)" else ""
        holder.binding.tvTitle.text = "$productName$nameSuffix (${s.productId})"
        holder.binding.tvLocation.text = "Ubicacion: ${s.location}"
        holder.binding.tvMeta.text = "Cantidad: ${s.quantity}"
        holder.binding.ivWarning.visibility =
            if (isOffline || isPendingDelete) android.view.View.VISIBLE else android.view.View.GONE
        val pendingTooltip = if (isPendingDelete) {
            "Pendiente de vaciado en sincronizacion offline"
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
            holder.binding.tvStockId.setTextColor(offlineColor)
            holder.binding.tvTitle.setTextColor(offlineColor)
            holder.binding.tvLocation.setTextColor(offlineColor)
            holder.binding.tvMeta.setTextColor(offlineColor)
        } else {
            holder.binding.tvStockId.setTextColor(holder.idColor)
            holder.binding.tvTitle.setTextColor(holder.titleColor)
            holder.binding.tvLocation.setTextColor(holder.locationColor)
            holder.binding.tvMeta.setTextColor(holder.metaColor)
        }
        holder.binding.root.setOnClickListener { onClick(s) }
    }

    fun submit(newItems: List<StockResponseDto>, productNameById: Map<Int, String>) {
        items.clear()
        items.addAll(newItems)
        this.productNameById = productNameById
        notifyDataSetChanged()
    }
}
