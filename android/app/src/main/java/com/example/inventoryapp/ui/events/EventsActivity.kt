package com.example.inventoryapp.ui.events

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
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

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

        snack = SendSnack(binding.root)
        session = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

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
        val location = binding.etLocation.text.toString().trim().ifBlank { "default" }
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
                        snack.showQueuedOffline("Guardado en modo offline")
                        loadEvents(withSnack = false)
                    } else {
                        snack.showError("Error: ${ex?.message ?: "sin detalle"}")
                    }
                }
            } catch (e: IOException) {
                OfflineQueue(this@EventsActivity).enqueue(PendingType.EVENT_CREATE, gson.toJson(dto))
                snack.showQueuedOffline("Guardado en modo offline")
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
                    val codes = res.body()!!.items.map { it.code }.distinct().sorted()
                    val values = if (codes.contains("default")) codes else listOf("default") + codes
                    val adapter = ArrayAdapter(this@EventsActivity, android.R.layout.simple_list_item_1, values)
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

    private fun loadEvents(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando eventos...")

        lifecycleScope.launch {
            try {
                val res = repo.listEvents(limit = 50, offset = 0)
                if (res.isSuccess) {
                    val remoteItems = res.getOrNull()!!.items.map { it.toRowUi() }
                    val pendingItems = buildPendingRows()
                    items = pendingItems + remoteItems
                    adapter.submit(items)
                    if (withSnack) snack.showSuccess("OK: Eventos cargados")
                } else {
                    if (withSnack) snack.showError("Error: ${res.exceptionOrNull()?.message ?: "sin detalle"}")
                    val pendingItems = buildPendingRows()
                    items = pendingItems
                    adapter.submit(items)
                }
            } catch (e: Exception) {
                if (withSnack) snack.showError("Error de red: ${e.message}")
                val pendingItems = buildPendingRows()
                items = pendingItems
                adapter.submit(items)
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
                    delta = 0,
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
                    delta = dto.delta,
                    createdAt = "offline",
                    status = "PENDING",
                    isPending = true,
                    pendingMessage = "Guardado en modo offline, pendiente de sincronizacion"
                )
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
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
