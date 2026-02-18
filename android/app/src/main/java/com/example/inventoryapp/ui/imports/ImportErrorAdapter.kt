package com.example.inventoryapp.ui.imports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.ImportErrorDto

class ImportErrorAdapter(
    private var items: List<ImportErrorDto>,
    private var iconRes: Int = R.drawable.lote
) : RecyclerView.Adapter<ImportErrorAdapter.ViewHolder>() {

    private var batchId: Int? = null

    fun submit(list: List<ImportErrorDto>, batchId: Int? = this.batchId, iconRes: Int = this.iconRes) {
        items = list
        this.batchId = batchId
        this.iconRes = iconRes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_error, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.ivBatchIcon.setImageResource(iconRes)
        holder.tvBatchLabel.text = if (iconRes == R.drawable.transfer) "Transfer ID" else "Lote ID"
        holder.tvBatchId.text = (batchId ?: "-").toString()
        holder.tvTitle.text = "Fila ${item.row_number} - ${item.error_code}"
        holder.tvMessage.text = item.message
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivBatchIcon: ImageView = view.findViewById(R.id.ivErrorBatchIcon)
        val tvBatchLabel: TextView = view.findViewById(R.id.tvErrorBatchLabel)
        val tvBatchId: TextView = view.findViewById(R.id.tvErrorBatchId)
        val tvTitle: TextView = view.findViewById(R.id.tvErrorTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvErrorMessage)
    }
}
