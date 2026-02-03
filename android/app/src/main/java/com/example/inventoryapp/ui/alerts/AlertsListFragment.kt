package com.example.inventoryapp.ui.alerts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.local.EventAlertDismissStore
import com.example.inventoryapp.data.local.SystemAlertStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.AlertResponseDto
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.databinding.FragmentAlertsListBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.events.EventAdapter
import com.example.inventoryapp.ui.events.EventRowUi
import kotlinx.coroutines.launch

class AlertsListFragment : Fragment() {

    private var _binding: FragmentAlertsListBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
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
        session = SessionManager(requireContext())
        snack = SendSnack(binding.root)
        systemStore = SystemAlertStore(requireContext())
        dismissedStore = EventAlertDismissStore(requireContext())

        alertAdapter = AlertListAdapter(emptyList()) { alert -> showAlertActions(alert) }
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

        failedEventAdapter = EventAdapter(emptyList()) { }
        binding.rvFailedEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFailedEvents.adapter = failedEventAdapter

        binding.btnClearSystem.setOnClickListener {
            systemStore.clearAll()
            loadSystemAlerts()
            snack.showSuccess("Alertas del sistema eliminadas")
        }

        binding.btnClearStock.setOnClickListener {
            clearStockAlerts()
        }

        binding.btnClearFailedEvents.setOnClickListener {
            if (lastFailedRows.isNotEmpty()) {
                dismissedStore.addAll(lastFailedRows.map { it.id })
                loadFailedEvents()
                snack.showSuccess("Eventos fallidos limpiados")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSystemAlerts()
        loadAlerts()
        loadFailedEvents()
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
                if (res.code() == 401) {
                    UiNotifier.show(requireActivity(), ApiErrorFormatter.format(401))
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    alertAdapter.submit(items)
                    binding.tvBackendAlertsEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
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
                if (res.code() == 401) {
                    UiNotifier.show(requireActivity(), ApiErrorFormatter.format(401))
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

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

    private fun showAlertActions(alert: AlertResponseDto) {
        val message = "Stock: ${alert.stockId}\n" +
            "Cantidad: ${alert.quantity}\n" +
            "Minimo: ${alert.minQuantity}\n" +
            "Estado: ${alert.alertStatus}\n" +
            "Creado: ${alert.createdAt}"

        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Alerta #${alert.id}")
            .setMessage(message)
            .setNegativeButton("Cerrar", null)

        if (alert.alertStatus == AlertStatusDto.PENDING) {
            builder.setPositiveButton("Marcar vista") { _, _ ->
                ackAlert(alert.id)
            }
        }

        builder.show()
    }

    private fun ackAlert(alertId: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.ackAlert(alertId)
                if (res.code() == 401) {
                    UiNotifier.show(requireActivity(), ApiErrorFormatter.format(401))
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful) {
                    snack.showSuccess("Alerta marcada como vista")
                    loadAlerts()
                } else {
                    snack.showError(ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
            }
        }
    }

    private fun clearStockAlerts() {
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
                } else {
                    snack.showError(ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
            }
        }
    }

    private fun EventResponseDto.toRowUi(): EventRowUi {
        val status = eventStatus ?: if (processed) "PROCESSED" else "PENDING"
        return EventRowUi(
            id = id,
            eventType = eventType,
            productId = productId,
            delta = delta,
            createdAt = createdAt,
            status = status,
            isPending = false,
            pendingMessage = null
        )
    }

    private fun goToLogin() {
        val i = Intent(requireContext(), LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        requireActivity().finish()
    }
}
