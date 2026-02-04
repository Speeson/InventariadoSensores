package com.example.inventoryapp.ui.products

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.databinding.ItemProductCardBinding

data class ProductRowUi(
    val product: ProductResponseDto,
    val categoryName: String?
)

class ProductListAdapter(
    private val onClick: (ProductResponseDto) -> Unit
) : RecyclerView.Adapter<ProductListAdapter.VH>() {

    private val items = mutableListOf<ProductRowUi>()

    inner class VH(val binding: ItemProductCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val p = row.product
        holder.binding.tvName.text = p.name
        holder.binding.tvSku.text = "SKU: ${p.sku}"
        holder.binding.tvBarcode.text = "Barcode: ${p.barcode ?: "-"}"
        val catLabel = row.categoryName ?: "ID ${p.categoryId}"
        holder.binding.tvCategory.text = "Categoria: $catLabel"
        holder.binding.tvMeta.text = "ID: ${p.id}  •  Updated: ${p.updatedAt}"
        val isOffline = p.id < 0 || p.name.contains("(offline)", ignoreCase = true)
        holder.binding.ivOfflineAlert.visibility =
            if (isOffline) android.view.View.VISIBLE else android.view.View.GONE
        val barcode = p.barcode?.trim()
        holder.binding.btnCopyBarcode.visibility =
            if (barcode.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        holder.binding.btnCopyBarcode.setOnClickListener {
            val ctx = holder.itemView.context
            if (!barcode.isNullOrBlank()) {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("barcode", barcode))
                Toast.makeText(ctx, "Barcode copiado", Toast.LENGTH_SHORT).show()
            }
        }
        holder.binding.root.setOnClickListener { onClick(p) }
    }

    fun submit(newItems: List<ProductRowUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
