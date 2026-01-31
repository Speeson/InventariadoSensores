package com.example.inventoryapp.ui.rotation

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventTypeDto
import com.example.inventoryapp.databinding.ActivityRotationBinding
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

class RotationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRotationBinding
    private val adapter = RotationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRotationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnRefresh.setOnClickListener { loadRotation() }

        loadRotation()
    }

    private fun loadRotation() {
        binding.progress.visibility = View.VISIBLE
        binding.empty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // 1) Productos (id -> nombre)
                val prodRes = NetworkModule.api.listProducts(limit = 500, offset = 0)
                if (!prodRes.isSuccessful || prodRes.body() == null) {
                    showError("No se pudo cargar productos (HTTP ${prodRes.code()})")
                    return@launch
                }
                val products = prodRes.body()!!.items.associateBy({ it.id }, { it.name })

                // 2) Eventos procesados (para que sea “real” en stock)
                val eventsRes = NetworkModule.api.listEvents(processed = true, limit = 500, offset = 0)
                if (!eventsRes.isSuccessful || eventsRes.body() == null) {
                    showError("No se pudo cargar eventos (HTTP ${eventsRes.code()})")
                    return@launch
                }

                val events = eventsRes.body()!!.items

                // 3) Agregar por producto
                val grouped = events.groupBy { it.productId }
                val rows = grouped.map { (productId, evs) ->
                    val inQty = evs.filter { it.eventType == EventTypeDto.SENSOR_IN }.sumOf { it.delta }
                    val outQty = evs.filter { it.eventType == EventTypeDto.SENSOR_OUT }.sumOf { it.delta }
                    val last = evs.maxByOrNull { safeParseTime(it.createdAt) }?.createdAt ?: "-"

                    RotationRow(
                        productId = productId,
                        productName = products[productId] ?: "Producto #$productId",
                        inQty = inQty,
                        outQty = outQty,
                        net = inQty - outQty,
                        eventsCount = evs.size,
                        lastDate = formatDate(last)
                    )
                }.sortedByDescending { it.outQty } // “rotación” típica: más salidas primero

                binding.progress.visibility = View.GONE

                if (rows.isEmpty()) {
                    binding.empty.visibility = View.VISIBLE
                    adapter.submitList(emptyList())
                } else {
                    binding.empty.visibility = View.GONE
                    adapter.submitList(rows)
                }

            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private fun showError(msg: String) {
        binding.progress.visibility = View.GONE
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun safeParseTime(s: String): Long {
        return try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatDate(s: String): String {
        // Si te llega ISO: "2026-01-31T10:20:30Z" -> mostramos solo fecha + hora corta
        return try {
            val odt = OffsetDateTime.parse(s)
            val d = odt.toLocalDate().toString()
            val t = odt.toLocalTime().withNano(0).toString()
            "$d $t"
        } catch (_: Exception) {
            s
        }
    }
}
