package com.example.inventoryapp.ui.movements

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R
import com.example.inventoryapp.databinding.ItemMovementCardBinding

data class MovementRow(
    val movementId: Int,
    val type: String,
    val title: String,
    val meta: String,
    val sub: String,
    val isPending: Boolean
)

class MovementsListAdapter : RecyclerView.Adapter<MovementsListAdapter.VH>() {

    private val items = mutableListOf<MovementRow>()

    inner class VH(val binding: ItemMovementCardBinding) : RecyclerView.ViewHolder(binding.root) {
        val typeColor = binding.tvTypeTag.currentTextColor
        val idColor = binding.tvMovementId.currentTextColor
        val titleColor = binding.tvTitle.currentTextColor
        val metaColor = binding.tvMeta.currentTextColor
        val subColor = binding.tvSub.currentTextColor
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMovementCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        holder.binding.tvTypeTag.text = row.type
        holder.binding.tvMovementId.text = if (row.movementId < 0) "ID offline" else "ID ${row.movementId}"
        holder.binding.tvTitle.text = row.title
        holder.binding.tvMeta.text = row.meta
        holder.binding.tvSub.text = row.sub
        val iconRes = when (row.type.uppercase()) {
            "IN" -> com.example.inventoryapp.R.drawable.triangle_down
            "OUT" -> com.example.inventoryapp.R.drawable.triangle_up
            "ADJUST" -> com.example.inventoryapp.R.drawable.adjust
            "TRANSFER" -> android.R.drawable.ic_menu_directions
            else -> android.R.drawable.ic_menu_help
        }
        holder.binding.ivIcon.setImageResource(iconRes)
        holder.binding.ivIcon.alpha = if (row.isPending) 0.7f else 1.0f
        holder.binding.ivWarning.visibility =
            if (row.isPending) android.view.View.VISIBLE else android.view.View.GONE
        val offlineColor = ContextCompat.getColor(holder.itemView.context, R.color.offline_text)
        if (row.isPending) {
            holder.binding.tvTypeTag.setTextColor(offlineColor)
            holder.binding.tvMovementId.setTextColor(offlineColor)
            holder.binding.tvTitle.setTextColor(offlineColor)
            holder.binding.tvMeta.setTextColor(offlineColor)
            holder.binding.tvSub.setTextColor(offlineColor)
        } else {
            holder.binding.tvTypeTag.setTextColor(holder.typeColor)
            holder.binding.tvMovementId.setTextColor(holder.idColor)
            holder.binding.tvTitle.setTextColor(holder.titleColor)
            holder.binding.tvMeta.setTextColor(holder.metaColor)
            holder.binding.tvSub.setTextColor(holder.subColor)
        }
    }

    fun submit(newItems: List<MovementRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
