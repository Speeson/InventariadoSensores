package com.example.inventoryapp.ui.imports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.ImportErrorDto

class ImportErrorAdapter(
    private var items: List<ImportErrorDto>
) : RecyclerView.Adapter<ImportErrorAdapter.ViewHolder>() {

    fun submit(list: List<ImportErrorDto>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_error, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = "Fila ${item.row_number} · ${item.error_code}"
        holder.tvMessage.text = item.message
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvErrorTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvErrorMessage)
    }
}
