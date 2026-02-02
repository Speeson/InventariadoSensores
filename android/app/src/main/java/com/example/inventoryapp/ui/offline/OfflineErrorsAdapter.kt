package com.example.inventoryapp.ui.offline

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.local.FailedRequest
import com.example.inventoryapp.databinding.ItemOfflineErrorCardBinding

class OfflineErrorsAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<OfflineErrorsAdapter.VH>() {

    private val items = mutableListOf<FailedRequest>()

    inner class VH(val binding: ItemOfflineErrorCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOfflineErrorCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val whenStr = DateFormat.format("dd/MM HH:mm", item.failedAt).toString()
        val codeStr = item.httpCode?.toString() ?: "-"

        holder.binding.tvTitle.text = "${item.original.type}  •  HTTP ${codeStr}"
        holder.binding.tvMeta.text = whenStr
        holder.binding.tvMessage.text = item.errorMessage
        holder.binding.root.setOnClickListener { onClick(position) }
    }

    fun submit(list: List<FailedRequest>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
