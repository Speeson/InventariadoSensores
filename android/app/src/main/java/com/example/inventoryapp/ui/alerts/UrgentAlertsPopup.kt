package com.example.inventoryapp.ui.alerts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.PopupAlertDismissStore
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import kotlinx.coroutines.launch

object UrgentAlertsPopup {

    private data class PopupItem(
        val stableId: String,
        val title: String,
        val subtitle: String,
        val timestamp: String,
        val iconRes: Int,
        var expanded: Boolean = false,
    )

    fun show(
        activity: AppCompatActivity,
        onDismiss: (() -> Unit)? = null,
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_urgent_alerts, null)
        val recycler = view.findViewById<RecyclerView>(R.id.rvUrgentAlerts)
        val empty = view.findViewById<TextView>(R.id.tvUrgentAlertsEmpty)
        val close = view.findViewById<ImageButton>(R.id.btnUrgentAlertsClose)
        val clearAll = view.findViewById<ImageButton>(R.id.btnUrgentAlertsClearAll)
        val title = view.findViewById<TextView>(R.id.tvUrgentAlertsTitle)

        val adapter = PopupAlertAdapter()
        val dismissedStore = PopupAlertDismissStore(activity)

        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = adapter

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        fun render(items: List<PopupItem>) {
            adapter.submit(items)
            empty.isVisible = items.isEmpty()
            title.text = "Alertas prioritarias (${items.size})"
            if (items.isEmpty()) {
                empty.text = "No hay alertas prioritarias ahora mismo"
            }
        }

        suspend fun buildItems(): List<PopupItem> {
            return UrgentAlertsRepository.load(activity).map {
                PopupItem(
                    stableId = it.stableId,
                    title = it.title,
                    subtitle = it.subtitle,
                    timestamp = it.timestamp,
                    iconRes = it.iconRes,
                )
            }
        }

        fun loadItems() {
            activity.lifecycleScope.launch {
                try {
                    val items = buildItems()
                    render(items)
                    AlertsBadgeUtil.applyCount(activity, items.size)
                } catch (e: Exception) {
                    render(emptyList())
                    empty.isVisible = true
                    empty.text = e.message ?: "No se pudieron cargar las alertas prioritarias"
                    AlertsBadgeUtil.applyCount(activity, 0)
                }
            }
        }

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = if (position != RecyclerView.NO_POSITION) adapter.itemAt(position) else null
                if (item != null) {
                    dismissedStore.add(item.stableId)
                    adapter.remove(item.stableId)
                    empty.isVisible = adapter.itemCount == 0
                    if (adapter.itemCount == 0) {
                        empty.text = "No hay alertas prioritarias ahora mismo"
                    }
                    title.text = "Alertas prioritarias (${adapter.itemCount})"
                    AlertsBadgeUtil.applyCount(activity, adapter.itemCount)
                } else {
                    loadItems()
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recycler)

        clearAll.setOnClickListener {
            dismissedStore.addAll(adapter.currentIds())
            render(emptyList())
            AlertsBadgeUtil.applyCount(activity, 0)
        }

        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { onDismiss?.invoke() }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        loadItems()
    }
    private class PopupAlertAdapter : RecyclerView.Adapter<PopupAlertAdapter.ViewHolder>() {
        private val items = mutableListOf<PopupItem>()

        fun submit(newItems: List<PopupItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun remove(stableId: String) {
            val index = items.indexOfFirst { it.stableId == stableId }
            if (index >= 0) {
                items.removeAt(index)
                notifyItemRemoved(index)
            }
        }

        fun itemAt(position: Int): PopupItem? = items.getOrNull(position)

        fun currentIds(): List<String> = items.map { it.stableId }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_urgent_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageResource(item.iconRes)
            holder.title.text = item.title
            holder.subtitle.text = item.subtitle
            holder.timestamp.text = item.timestamp
            holder.details.visibility = if (item.expanded) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                item.expanded = !item.expanded
                val currentPosition = holder.adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(currentPosition)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivUrgentItemIcon)
            val title: TextView = view.findViewById(R.id.tvUrgentItemTitle)
            val details: LinearLayout = view.findViewById(R.id.layoutUrgentItemDetails)
            val subtitle: TextView = view.findViewById(R.id.tvUrgentItemSubtitle)
            val timestamp: TextView = view.findViewById(R.id.tvUrgentItemTimestamp)
        }
    }
}
