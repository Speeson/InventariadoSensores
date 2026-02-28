package com.example.inventoryapp.ui.imports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R

class ImportErrorAdapter(
    private var items: List<ImportErrorRow>
) : RecyclerView.Adapter<ImportErrorAdapter.ViewHolder>() {

    fun submit(list: List<ImportErrorRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_error, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvBatchLabel.text = "Lote ID"
        holder.tvBatchId.text = (item.batchId ?: "-").toString()
        holder.tvTitle.text = "Fila ${item.rowNumber} - ${item.errorCode}"
        holder.tvMessage.text = item.message
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBatchLabel: TextView = view.findViewById(R.id.tvErrorBatchLabel)
        val tvBatchId: TextView = view.findViewById(R.id.tvErrorBatchId)
        val tvTitle: TextView = view.findViewById(R.id.tvErrorTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvErrorMessage)
    }
}

data class ImportErrorRow(
    val rowNumber: Int,
    val errorCode: String,
    val message: String,
    val batchId: Int?,
    val iconRes: Int
)
