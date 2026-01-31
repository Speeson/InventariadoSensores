package com.example.inventoryapp.ui.alerts

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.inventoryapp.data.remote.model.AlertDto

class AlertAdapter(
    private val ctx: Context,
    private val items: List<AlertDto>,
    private val onAck: (AlertDto) -> Unit
) : BaseAdapter() {

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(ctx)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        val alert = items[position]
        v.findViewById<TextView>(android.R.id.text1).text =
            "Producto ${alert.productId} • ${alert.status.uppercase()}"

        v.findViewById<TextView>(android.R.id.text2).text =
            alert.message

        v.setOnClickListener {
            if (alert.status == "pending") {
                onAck(alert)
            }
        }
        return v
    }
}