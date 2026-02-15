package com.example.inventoryapp.ui.audit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.AuditLogResponseDto
import com.example.inventoryapp.databinding.ItemAuditLogBinding
import com.example.inventoryapp.ui.common.GradientIconUtil

class AuditAdapter(
    private val onClick: (AuditLogResponseDto) -> Unit
) : RecyclerView.Adapter<AuditAdapter.AuditViewHolder>() {

    private var items: List<AuditLogResponseDto> = emptyList()

    fun submit(newItems: List<AuditLogResponseDto>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuditViewHolder {
        val binding = ItemAuditLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AuditViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AuditViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class AuditViewHolder(
        private val binding: ItemAuditLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AuditLogResponseDto, onClick: (AuditLogResponseDto) -> Unit) {
            binding.tvAuditIdValue.text = item.id.toString()
            binding.tvAuditHeader.text = "${item.action} - ${item.entity}"
            binding.tvAuditMeta.text = "user ${item.user_id} - ${item.created_at}"
            binding.tvAuditDetails.text = item.details ?: "Sin detalles"
            GradientIconUtil.applyGradient(binding.ivAuditIcon, R.drawable.system)
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
