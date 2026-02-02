package com.example.inventoryapp.ui.movements

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.databinding.ItemMovementCardBinding

data class MovementRow(
    val title: String,
    val meta: String,
    val sub: String,
    val isPending: Boolean,
    val type: String
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
        holder.binding.tvTitle.text = row.title
        holder.binding.tvMeta.text = row.meta
        holder.binding.tvSub.text = row.sub
        val iconRes = when (row.type.uppercase()) {
            "IN" -> android.R.drawable.arrow_down_float
            "OUT" -> android.R.drawable.arrow_up_float
            "ADJUST" -> android.R.drawable.ic_menu_manage
            "TRANSFER" -> android.R.drawable.ic_menu_directions
            else -> android.R.drawable.ic_menu_help
        }
        holder.binding.ivIcon.setImageResource(iconRes)
        val color = if (row.isPending) 0xFFFFA000.toInt() else 0xFF4CAF50.toInt()
        holder.binding.ivIcon.setColorFilter(color)
    }

    fun submit(newItems: List<MovementRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
