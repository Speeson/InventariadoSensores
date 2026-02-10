package com.example.inventoryapp.ui.rotation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.ui.common.GradientIconUtil
import java.text.NumberFormat
import java.util.Locale

class RotationAdapter(
    private var items: List<RotationRow>
) : RecyclerView.Adapter<RotationAdapter.ViewHolder>() {

    fun submit(list: List<RotationRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rotation_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvMovementIdValue.text = item.movementId.toString()
        holder.tvProduct.text = "Producto: ${item.name}"
        holder.tvSkuQty.text = "SKU: ${item.sku} - Cantidad: ${formatQuantity(item.quantity)} uds."
        holder.tvFrom.text = "Origen: ${item.fromLocation}"
        holder.tvTo.text = "Destino: ${item.toLocation}"
        holder.tvDate.text = item.createdAt
        GradientIconUtil.applyGradient(holder.ivIcon, R.drawable.rotations)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivRotationIcon)
        val tvMovementIdValue: TextView = view.findViewById(R.id.tvRotationMovementIdValue)
        val tvProduct: TextView = view.findViewById(R.id.tvRotationProduct)
        val tvSkuQty: TextView = view.findViewById(R.id.tvRotationSkuQty)
        val tvFrom: TextView = view.findViewById(R.id.tvRotationFrom)
        val tvTo: TextView = view.findViewById(R.id.tvRotationTo)
        val tvDate: TextView = view.findViewById(R.id.tvRotationDate)
    }

    private fun formatQuantity(value: Int): String {
        return NumberFormat.getInstance(Locale("es", "ES")).format(value)
    }
}
