package com.example.inventoryapp.ui.products

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.databinding.ItemProductCardBinding

data class ProductRowUi(
    val product: ProductResponseDto,
    val categoryName: String?
)

class ProductListAdapter(
    private val onClick: (ProductResponseDto) -> Unit,
    private val onLabelClick: (ProductResponseDto) -> Unit
) : RecyclerView.Adapter<ProductListAdapter.VH>() {

    private val items = mutableListOf<ProductRowUi>()
    private var gradientIcon: Bitmap? = null

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
        holder.binding.ivIcon.setImageBitmap(getGradientIcon(holder.itemView.context))
        val catName = row.categoryName ?: "N/D"
        val barcodeText = p.barcode ?: "-"
        holder.binding.tvDetails.text =
            "SKU: ${p.sku}\n" +
            "Barcode: $barcodeText\n" +
            "Categoria: $catName (ID ${p.categoryId})  •  ID: ${p.id}\n" +
            "Updated: ${p.updatedAt}"
        val isOffline = p.id < 0 || p.name.contains("(offline)", ignoreCase = true)
        holder.binding.ivOfflineAlert.visibility =
            if (isOffline) android.view.View.VISIBLE else android.view.View.GONE
        val barcode = p.barcode?.trim()
        holder.binding.btnCopyBarcode.visibility =
            if (barcode.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        holder.binding.btnLabel.visibility =
            if (barcode.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        holder.binding.btnCopyBarcode.setOnClickListener {
            val ctx = holder.itemView.context
            if (!barcode.isNullOrBlank()) {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("barcode", barcode))
                Toast.makeText(ctx, "Barcode copiado", Toast.LENGTH_SHORT).show()
            }
        }
        holder.binding.btnLabel.setOnClickListener {
            onLabelClick(p)
        }
        holder.binding.root.setOnClickListener { onClick(p) }
    }

    fun submit(newItems: List<ProductRowUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun getGradientIcon(context: Context): Bitmap {
        gradientIcon?.let { return it }
        val src = BitmapFactory.decodeResource(context.resources, R.drawable.products)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val colors = intArrayOf(
            ContextCompat.getColor(context, R.color.icon_grad_start),  // light blue (top)
            ContextCompat.getColor(context, R.color.icon_grad_mid2),   // dark blue (mid)
            ContextCompat.getColor(context, R.color.icon_grad_mid1),   // pink (lower-mid)
            ContextCompat.getColor(context, R.color.icon_grad_end)     // violet (bottom)
        )
        val shader = LinearGradient(
            0f,
            0f,
            src.width.toFloat(),
            src.height.toFloat(),
            colors,
            floatArrayOf(0f, 0.33f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        paint.xfermode = null
        gradientIcon = out
        return out
    }
}
