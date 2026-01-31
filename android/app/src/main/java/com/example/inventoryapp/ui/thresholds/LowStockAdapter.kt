package com.example.inventoryapp.ui.thresholds

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.inventoryapp.data.remote.model.LowStockDto

class LowStockAdapter(
    context: Context,
    private val items: List<LowStockDto>,
    private val onEdit: (LowStockDto) -> Unit
) : ArrayAdapter<LowStockDto>(context, android.R.layout.simple_list_item_2, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        val item = items[position]

        view.findViewById<TextView>(android.R.id.text1).text =
            "${item.productName} (${item.location})"

        view.findViewById<TextView>(android.R.id.text2).text =
            "Cantidad=${item.quantity} | Umbral=${item.threshold}"

        view.setOnClickListener { onEdit(item) }

        return view
    }
}