package com.example.inventoryapp.ui.events

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.databinding.ItemEventRowBinding

/**
 * ✅ TC-104
 * Adapter que pinta cada evento como una fila de tabla simple.
 */
class EventAdapter(
    private var items: List<EventResponseDto> = emptyList()
) : RecyclerView.Adapter<EventAdapter.EventVH>() {

    /** ViewHolder: une el layout item_event_row.xml con Kotlin */
    class EventVH(val binding: ItemEventRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEventRowBinding.inflate(inflater, parent, false)
        return EventVH(binding)
    }

    override fun onBindViewHolder(holder: EventVH, position: Int) {
        val e = items[position]

        // Columna 1: ID
        holder.binding.tvId.text = "#${e.id}"

        // Columna 2: Tipo
        holder.binding.tvType.text = e.eventType.name

        // Columna 3: Product ID
        holder.binding.tvProduct.text = e.productId.toString()

        // Columna 4: Delta
        holder.binding.tvDelta.text = e.delta.toString()

        // Columna 5: Procesado (✅ si processed=true, ⏳ si false)
        holder.binding.tvProcessed.text = if (e.processed) "✅" else "⏳"

        // Columna 6: Info extra (source + fecha recortada)
        val shortDate = e.createdAt.take(19).replace("T", " ")
        holder.binding.tvInfo.text = "${e.source} / $shortDate"
    }

    override fun getItemCount(): Int = items.size

    /**
     * Permite actualizar la lista sin recrear el adapter.
     */
    fun submit(newItems: List<EventResponseDto>) {
        items = newItems
        notifyDataSetChanged()
    }
}
