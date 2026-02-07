package com.example.inventoryapp.ui.movements

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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

    inner class VH(val binding: ItemMovementCardBinding) : RecyclerView.ViewHolder(binding.root)

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
    }

    fun submit(newItems: List<MovementRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
