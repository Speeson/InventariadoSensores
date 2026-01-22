package com.example.inventoryapp.ui.events

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.data.remote.model.EventTypeDto
import com.example.inventoryapp.databinding.ActivityEventsBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack

    private val gson = Gson()
    private var items: List<EventResponseDto> = emptyList()

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
            location = location
        )

        binding.btnCreateEvent.isEnabled = false
        snack.showSending("Enviando evento...")

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createEvent(dto)

                if (res.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful) {
                    snack.showSuccess("✅ Evento creado")
                    binding.etDelta.setText("")
                    loadEvents(withSnack = false)
                } else {
                    snack.showError("❌ Error ${res.code()}: ${res.errorBody()?.string() ?: "sin detalle"}")
                }

            } catch (e: IOException) {
                OfflineQueue(this@EventsActivity).enqueue(PendingType.EVENT_CREATE, gson.toJson(dto))
                snack.showQueuedOffline("📦 Sin red/backend caído. Evento guardado offline ✅")

            } catch (e: Exception) {
                snack.showError("❌ Error: ${e.message}")

            } finally {
                binding.btnCreateEvent.isEnabled = true
            }
        }
    }

    private fun loadEvents(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando eventos...")

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listEvents(limit = 50, offset = 0)

                if (res.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    items = res.body()!!.items
                    val lines = items.map { "#${it.id} ${it.eventType} prod=${it.productId} Δ=${it.delta} proc=${it.processed}" }
                    binding.lvEvents.adapter = ArrayAdapter(this@EventsActivity, android.R.layout.simple_list_item_1, lines)
                    if (withSnack) snack.showSuccess("✅ Eventos cargados")
                } else {
                    if (withSnack) snack.showError("❌ Error ${res.code()}: ${res.errorBody()?.string() ?: "sin detalle"}")
                }

            } catch (e: Exception) {
                if (withSnack) snack.showError("❌ Error de red: ${e.message}")
            }
        }
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
