package com.example.inventoryapp.ui.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.local.EventAlertDismissStore
import com.example.inventoryapp.data.local.SystemAlertStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.AlertsWebSocketManager
import com.example.inventoryapp.data.remote.model.AlertResponseDto
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.data.remote.model.AlertTypeDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.databinding.FragmentAlertsListBinding
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.events.EventAdapter
import com.example.inventoryapp.ui.events.EventRowUi
import kotlinx.coroutines.launch

class AlertsListFragment : Fragment() {

    private var _binding: FragmentAlertsListBinding? = null
    private val binding get() = _binding!!

    private lateinit var snack: SendSnack
    private lateinit var alertAdapter: AlertListAdapter
    private lateinit var failedEventAdapter: EventAdapter
    private lateinit var systemAdapter: SystemAlertAdapter
    private lateinit var systemStore: SystemAlertStore
    private lateinit var dismissedStore: EventAlertDismissStore
    private var lastFailedRows: List<EventRowUi> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        snack = SendSnack(binding.root)
        systemStore = SystemAlertStore(requireContext())
        dismissedStore = EventAlertDismissStore(requireContext())

        alertAdapter = AlertListAdapter(emptyList()) { row -> showAlertActions(row) }
        binding.rvAlerts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAlerts.adapter = alertAdapter

        systemAdapter = SystemAlertAdapter(emptyList()) { alert ->
            AlertDialog.Builder(requireContext())
                .setTitle(alert.title)
                .setMessage(alert.message)
                .setPositiveButton("Aceptar") { _, _ ->
                    systemStore.markSeen(alert.id)
                    loadSystemAlerts()
                }
                .show()
        }
        binding.rvSystemAlerts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSystemAlerts.adapter = systemAdapter
        attachSystemSwipe()

        failedEventAdapter = EventAdapter(emptyList())
        binding.rvFailedEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFailedEvents.adapter = failedEventAdapter

        binding.btnClearSystem.setOnClickListener {
            systemStore.clearAll()
            loadSystemAlerts()
            snack.showSuccess("Alertas del sistema eliminadas")
        }

        binding.btnClearStock.setOnClickListener {
            if (isUserRole()) {
                showStockPermissionDenied()
            } else {
                clearStockAlerts()
            }
        }

        binding.btnClearFailedEvents.setOnClickListener {
            if (lastFailedRows.isNotEmpty()) {
                dismissedStore.addAll(lastFailedRows.map { it.id })
                loadFailedEvents()
                snack.showSuccess("Eventos fallidos limpiados")
            }
        }

        applyRoleRestrictions()
    }

    override fun onResume() {
        super.onResume()
        loadSystemAlerts()
        loadAlerts()
        loadFailedEvents()
        observeWebSocketAlerts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadSystemAlerts() {
        val items = systemStore.list()
        systemAdapter.submit(items)
        binding.tvAlertsEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun attachSystemSwipe() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val items = systemStore.list()
                if (position in items.indices) {
                    systemStore.remove(items[position].id)
                    loadSystemAlerts()
                    snack.showSuccess("Alerta eliminada")
                } else {
                    loadSystemAlerts()
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvSystemAlerts)
    }

    private fun loadAlerts() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listAlerts(limit = 50, offset = 0)
                if (res.code() == 401) return@launch

                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val rows = enrichAlerts(items)
                        .filter { isAlertVisibleForUser(it.alert) }
                        .sortedWith(
                            compareBy<AlertRowUi> { statusWeight(it.alert.alertStatus) }
                                .thenByDescending { it.alert.id }
                        )
                    alertAdapter.submit(rows)
                    binding.tvBackendAlertsEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    snack.showError(ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
            }
        }
    }

    private fun loadFailedEvents() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listEvents(limit = 100, offset = 0)
                if (res.code() == 401) return@launch

                if (res.isSuccessful && res.body() != null) {
                    val rows = res.body()!!.items
                        .map { it.toRowUi() }
                        .filter { it.status.uppercase() == "ERROR" || it.status.uppercase() == "FAILED" }
                        .filterNot { dismissedStore.list().contains(it.id) }
                    lastFailedRows = rows
                    failedEventAdapter.submit(rows)
                    binding.tvEventsEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    snack.showError(ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
            }
        }
    }

    private fun showAlertActions(row: AlertRowUi) {
        val alert = row.alert
        val productLabel = row.productName ?: (alert.stockId?.let { "Producto $it" } ?: "Importación")
        val locationLabel = row.location ?: "N/D"
        val message = "Producto: $productLabel\n" +
            "Cantidad: ${alert.quantity}\n" +
            "Minimo: ${alert.minQuantity}\n" +
            "Location: $locationLabel\n" +
            "Estado: ${alert.alertStatus}\n" +
            "Creado: ${alert.createdAt}"

        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Alerta #${alert.id}")
            .setMessage(message)
            .setNegativeButton("Cerrar", null)

        if (alert.alertStatus == AlertStatusDto.PENDING) {
            builder.setPositiveButton("Marcar vista") { _, _ ->
                if (isUserRole()) {
                    showStockPermissionDenied()
                } else {
                    ackAlert(alert.id)
                }
            }
        }

        builder.show()
    }

    private fun ackAlert(alertId: Int) {
        if (isUserRole()) {
            showStockPermissionDenied()
            return
        }
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.ackAlert(alertId)
                if (res.code() == 401) return@launch

                if (res.isSuccessful) {
                    snack.showSuccess("Alerta marcada como vista")
                    loadAlerts()
                } else if (res.code() == 403) {
                    showStockPermissionDenied()
                } else {
                    snack.showError(ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
            }
        }
    }

    private fun clearStockAlerts() {
        if (isUserRole()) {
            showStockPermissionDenied()
            return
        }
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listAlerts(status = AlertStatusDto.PENDING, limit = 100, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    if (items.isEmpty()) {
                        snack.showSuccess("No hay alertas de stock")
                        return@launch
                    }
                    items.forEach { NetworkModule.api.ackAlert(it.id) }
                    loadAlerts()
                    snack.showSuccess("Alertas de stock marcadas como vistas")
                } else if (res.code() == 403) {
                    showStockPermissionDenied()
                } else {
                    snack.showError(ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
            }
        }
    }

    private suspend fun enrichAlerts(items: List<AlertResponseDto>): List<AlertRowUi> {
        return items.map { alert ->
            var productName: String? = null
            var location: String? = null
            try {
                val stockId = alert.stockId
                if (stockId != null) {
                    val stockRes = NetworkModule.api.getStock(stockId)
                    if (stockRes.isSuccessful && stockRes.body() != null) {
                        val stock = stockRes.body()!!
                        location = stock.location
                        val productRes = NetworkModule.api.getProduct(stock.productId)
                        if (productRes.isSuccessful && productRes.body() != null) {
                            productName = productRes.body()!!.name
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep fallback labels if lookup fails.
            }
            AlertRowUi(alert = alert, productName = productName, location = location)
        }
    }

    private fun observeWebSocketAlerts() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            AlertsWebSocketManager.alerts.collect {
                loadAlerts()
            }
        }
    }

    private fun statusWeight(status: AlertStatusDto): Int {
        return when (status) {
            AlertStatusDto.PENDING -> 0
            AlertStatusDto.RESOLVED -> 1
            AlertStatusDto.ACK -> 2
        }
    }

    private fun isAlertVisibleForUser(alert: AlertResponseDto): Boolean {
        val prefs = requireContext().getSharedPreferences("ui_prefs", 0)
        val role = prefs.getString("cached_role", null) ?: return true
        if (!role.equals("USER", ignoreCase = true)) return true
        return alert.alertType == AlertTypeDto.LOW_STOCK || alert.alertType == AlertTypeDto.OUT_OF_STOCK
    }

    private fun isUserRole(): Boolean {
        val role = requireContext().getSharedPreferences("ui_prefs", 0).getString("cached_role", null)
        return role.equals("USER", ignoreCase = true)
    }

    private fun showStockPermissionDenied() {
        UiNotifier.showBlocking(
            requireActivity(),
            "Permisos insuficientes",
            "No tienes permisos para gestionar alertas de stock.",
            com.example.inventoryapp.R.drawable.ic_lock
        )
    }

    private fun applyRoleRestrictions() {
        if (!isUserRole()) return
        binding.btnClearStock.apply {
            text = "Bloqueado"
            setCompoundDrawablesWithIntrinsicBounds(com.example.inventoryapp.R.drawable.ic_lock, 0, 0, 0)
            compoundDrawablePadding = 10
            isAllCaps = false
        }
    }

    private fun EventResponseDto.toRowUi(): EventRowUi {
        val status = eventStatus ?: if (processed) "PROCESSED" else "PENDING"
        return EventRowUi(
            id = id,
            eventType = eventType,
            productId = productId,
            productName = null,
            delta = delta,
            source = source,
            createdAt = createdAt,
            status = status,
            isPending = false,
            pendingMessage = null
        )
    }

}
