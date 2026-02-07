package com.example.inventoryapp.ui.events
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.data.remote.model.EventTypeDto
import com.example.inventoryapp.data.repository.remote.EventRepository
import com.example.inventoryapp.databinding.ActivityEventsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import com.example.inventoryapp.ui.common.GradientIconUtil


class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack

    private val gson = Gson()
    private val repo = EventRepository()
    private var items: List<EventRowUi> = emptyList()
    private lateinit var adapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
snack = SendSnack(binding.root)
        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnCreateEvent.setOnClickListener { createEvent() }
        binding.btnRefresh.setOnClickListener { loadEvents(withSnack = true) }

        adapter = EventAdapter(emptyList()) { row ->
            snack.showError(row.pendingMessage ?: "Guardado en modo offline")
        }
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter

        setupLocationDropdown()
    }

    override fun onResume() {
        super.onResume()
        loadEvents(withSnack = false)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun createEvent() {
        val typeRaw = binding.etEventType.text.toString().trim().uppercase()
        val productId = binding.etProductId.text.toString().trim().toIntOrNull()
        val delta = binding.etDelta.text.toString().trim().toIntOrNull()
        val location = normalizeLocationInput(binding.etLocation.text.toString().trim()).ifBlank { "default" }
        val source = binding.etSource.text.toString().trim().ifBlank { "sensor_simulado" }

        val eventType = when (typeRaw) {
            "SENSOR_IN" -> EventTypeDto.SENSOR_IN
            "SENSOR_OUT" -> EventTypeDto.SENSOR_OUT
            else -> { binding.etEventType.error = "Usa SENSOR_IN o SENSOR_OUT"; return }
        }

        if (productId == null) { binding.etProductId.error = "Product ID requerido"; return }
        if (delta == null || delta <= 0) { binding.etDelta.error = "Delta debe ser > 0"; return }

        val dto = EventCreateDto(
            eventType = eventType,
            productId = productId,
            delta = delta,
            source = source,
            location = location,
            idempotencyKey = UUID.randomUUID().toString()
        )

        binding.btnCreateEvent.isEnabled = false
        snack.showSending("Enviando evento...")

        lifecycleScope.launch {
            try {
                val res = repo.createEvent(dto)
                if (res.isSuccess) {
                    snack.showSuccess("OK: Evento creado")
                    binding.etDelta.setText("")
                    loadEvents(withSnack = false)
                } else {
                    val ex = res.exceptionOrNull()
                    if (ex is IOException) {
                        OfflineQueue(this@EventsActivity).enqueue(PendingType.EVENT_CREATE, gson.toJson(dto))
                        snack.showQueuedOffline("Sin conexión. Evento guardado offline")
                        loadEvents(withSnack = false)
                    } else {
                        if (isForbidden(ex)) {
                            UiNotifier.showBlocking(
                                this@EventsActivity,
                                "Permisos insuficientes",
                                "No tienes permisos para crear eventos.",
                                com.example.inventoryapp.R.drawable.ic_lock
                            )
                        } else {
                            snack.showError("Error: ${ex?.message ?: "sin detalle"}")
                        }
                    }
                }
            } catch (e: IOException) {
                OfflineQueue(this@EventsActivity).enqueue(PendingType.EVENT_CREATE, gson.toJson(dto))
                snack.showQueuedOffline("Sin conexión. Evento guardado offline")
                loadEvents(withSnack = false)
            } catch (e: Exception) {
                snack.showError("Error: ${e.message}")
            } finally {
                binding.btnCreateEvent.isEnabled = true
            }
        }
    }

    private fun setupLocationDropdown() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val values = items.map { "(${it.id}) ${it.code}" }.distinct().sorted()
                    val allValues = if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = ArrayAdapter(this@EventsActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etLocation.setAdapter(adapter)
                    binding.etLocation.setOnClickListener { binding.etLocation.showDropDown() }
                    binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etLocation.showDropDown()
                    }
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
            }
        }
    }


    private fun isForbidden(ex: Throwable?): Boolean {
        val msg = ex?.message ?: return false
        return msg.contains("HTTP 403")
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun loadEvents(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando eventos...")

        lifecycleScope.launch {
            try {
                val res = repo.listEvents(limit = 50, offset = 0)
                if (res.isSuccess) {
                    val remoteEvents = res.getOrNull()!!.items
                    val pendingItems = buildPendingRows()
                    val allProductIds = mutableSetOf<Int>()
                    remoteEvents.forEach { allProductIds.add(it.productId) }
                    pendingItems.forEach { allProductIds.add(it.productId) }
                    val productNames = fetchProductNames(allProductIds)

                    val remoteItems = remoteEvents.map { it.toRowUi(productNames) }
                    val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                    val ordered = (pendingWithNames + remoteItems).sortedByDescending { it.id }
                    items = ordered
                    adapter.submit(ordered)
                    if (withSnack) snack.showSuccess("OK: Eventos cargados")
                } else {
                    val ex = res.exceptionOrNull()
                    if (withSnack) {
                        if (ex is IOException) {
                            snack.showError("Sin conexión a Internet")
                        } else {
                            snack.showError("Error: ${ex?.message ?: "sin detalle"}")
                        }
                    }
                    val pendingItems = buildPendingRows()
                    val productNames = fetchProductNames(pendingItems.map { it.productId }.toSet())
                    val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                    val ordered = pendingWithNames.sortedByDescending { it.id }
                    items = ordered
                    adapter.submit(ordered)
                }
            } catch (e: Exception) {
                if (withSnack) {
                    if (e is IOException) {
                        snack.showError("Sin conexión a Internet")
                    } else {
                        snack.showError("Error de red: ${e.message}")
                    }
                }
                val pendingItems = buildPendingRows()
                val productNames = fetchProductNames(pendingItems.map { it.productId }.toSet())
                val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                val ordered = pendingWithNames.sortedByDescending { it.id }
                items = ordered
                adapter.submit(ordered)
            }
        }
    }

    private fun buildPendingRows(): List<EventRowUi> {
        val queue = OfflineQueue(this)
        val pending = queue.getAll().filter { it.type == PendingType.EVENT_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, EventCreateDto::class.java) }.getOrNull()
            if (dto == null) {
                EventRowUi(
                    id = -1 - index,
                    eventType = EventTypeDto.SENSOR_IN,
                    productId = 0,
                    productName = null,
                    delta = 0,
                    source = "offline",
                    createdAt = "offline",
                    status = "PENDING",
                    isPending = true,
                    pendingMessage = "Guardado en modo offline, pendiente de sincronizacion"
                )
            } else {
                EventRowUi(
                    id = -1 - index,
                    eventType = dto.eventType,
                    productId = dto.productId,
                    productName = null,
                    delta = dto.delta,
                    source = dto.source,
                    createdAt = "offline",
                    status = "PENDING",
                    isPending = true,
                    pendingMessage = "Guardado en modo offline, pendiente de sincronizacion"
                )
            }
        }
    }

    private fun EventResponseDto.toRowUi(productNames: Map<Int, String>): EventRowUi {
        val status = eventStatus ?: if (processed) "PROCESSED" else "PENDING"
        return EventRowUi(
            id = id,
            eventType = eventType,
            productId = productId,
            productName = productNames[productId],
            delta = delta,
            source = source,
            createdAt = createdAt,
            status = status,
            isPending = false,
            pendingMessage = null
        )
    }

    private suspend fun fetchProductNames(ids: Set<Int>): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        ids.forEach { id ->
            try {
                val res = NetworkModule.api.getProduct(id)
                if (res.isSuccessful && res.body() != null) {
                    out[id] = res.body()!!.name
                }
            } catch (_: Exception) {
                // Keep fallback labels if lookup fails.
            }
        }
        return out
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
