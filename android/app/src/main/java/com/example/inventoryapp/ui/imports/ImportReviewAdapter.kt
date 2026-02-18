package com.example.inventoryapp.ui.imports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.ImportReviewItemDto

class ImportReviewAdapter(
    private var items: List<ImportReviewItemDto>,
    private val onClick: (ImportReviewItemDto) -> Unit
) : RecyclerView.Adapter<ImportReviewAdapter.ViewHolder>() {

    fun submit(list: List<ImportReviewItemDto>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val iconRes = resolveIcon(item)
        holder.ivBatchIcon.setImageResource(iconRes)
        holder.tvBatchLabel.text = if (iconRes == R.drawable.transfer) "Transfer ID" else "Lote ID"
        holder.tvTitle.text = "Fila ${item.row_number} - Review #${item.id}"
        holder.tvMeta.text = buildMeta(item)
        holder.tvReason.text = item.reason
        holder.tvBatchId.text = item.batch_id.toString()
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    private fun buildMeta(item: ImportReviewItemDto): String {
        val name = item.payload["name"]?.toString()?.ifBlank { "-" } ?: "-"
        val sku = item.payload["sku"]?.toString()?.ifBlank { "-" } ?: "-"
        val qty = item.payload["quantity"]?.toString()?.ifBlank { "-" } ?: "-"
        return "Nombre: $name, SKU: $sku, Cantidad: $qty"
    }

    private fun resolveIcon(item: ImportReviewItemDto): Int {
        val hasTransferFields =
            item.payload["from_location_id"] != null || item.payload["to_location_id"] != null
        return if (hasTransferFields) R.drawable.transfer else R.drawable.lote
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivBatchIcon: ImageView = view.findViewById(R.id.ivReviewBatchIcon)
        val tvBatchLabel: TextView = view.findViewById(R.id.tvReviewBatchLabel)
        val tvBatchId: TextView = view.findViewById(R.id.tvReviewBatchId)
        val tvTitle: TextView = view.findViewById(R.id.tvReviewTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvReviewMeta)
        val tvReason: TextView = view.findViewById(R.id.tvReviewReason)
    }
}
