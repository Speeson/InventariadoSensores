package com.example.inventoryapp.ui.events

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
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
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private lateinit var session: SessionManager
    private val gson = Gson()

    private var items: List<EventResponseDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnCreateEvent.setOnClickListener { createEvent() }
        binding.btnRefresh.setOnClickListener { loadEvents() }
    }

    override fun onResume() {
        super.onResume()
        loadEvents()
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

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createEvent(dto)

                if (res.code() == 401) {
                    Toast.makeText(this@EventsActivity, "Sesión caducada. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful) {
                    Toast.makeText(this@EventsActivity, "Evento creado ✅", Toast.LENGTH_SHORT).show()
                    binding.etDelta.setText("")
                    loadEvents()
                } else {
                    Toast.makeText(this@EventsActivity, "Error ${res.code()}: ${res.errorBody()?.string() ?: "sin detalle"}", Toast.LENGTH_LONG).show()
                }

            } catch (e: IOException) {
                OfflineQueue(this@EventsActivity).enqueue(PendingType.EVENT_CREATE, gson.toJson(dto))
                Toast.makeText(this@EventsActivity, "Backend caído/sin red. Evento guardado para reenviar ✅", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(this@EventsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()

            } finally {
                binding.btnCreateEvent.isEnabled = true
            }
        }
    }

    private fun loadEvents() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listEvents(limit = 50, offset = 0)

                if (res.code() == 401) {
                    Toast.makeText(this@EventsActivity, "Sesión caducada. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    items = res.body()!!.items
                    val lines = items.map { "#${it.id} ${it.eventType} prod=${it.productId} Δ=${it.delta} proc=${it.processed}" }
                    binding.lvEvents.adapter = ArrayAdapter(this@EventsActivity, android.R.layout.simple_list_item_1, lines)
                } else {
                    Toast.makeText(this@EventsActivity, "Error ${res.code()}: ${res.errorBody()?.string() ?: "sin detalle"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EventsActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
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
