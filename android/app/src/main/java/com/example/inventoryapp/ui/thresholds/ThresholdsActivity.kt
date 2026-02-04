package com.example.inventoryapp.ui.thresholds

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ThresholdCreateDto
import com.example.inventoryapp.data.remote.model.ThresholdResponseDto
import com.example.inventoryapp.data.remote.model.ThresholdUpdateDto
import com.example.inventoryapp.databinding.ActivityThresholdsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.auth.LoginActivity
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class ThresholdsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdsBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: ThresholdListAdapter
    private var items: List<ThresholdResponseDto> = emptyList()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = ThresholdListAdapter { threshold ->
            showEditDialog(threshold)
        }
        binding.rvThresholds.layoutManager = LinearLayoutManager(this)
        binding.rvThresholds.adapter = adapter

        binding.btnCreate.setOnClickListener { createThreshold() }
        binding.btnSearch.setOnClickListener { search() }
        binding.btnClear.setOnClickListener {
            binding.etSearchProductId.setText("")
            binding.etSearchLocation.setText("")
            loadThresholds()
        }
    }

    override fun onResume() {
        super.onResume()
        loadThresholds()
    }

    private fun search() {
        val productId = binding.etSearchProductId.text.toString().trim().toIntOrNull()
        val location = binding.etSearchLocation.text.toString().trim().ifBlank { null }
        loadThresholds(productId = productId, location = location)
    }

    private fun loadThresholds(productId: Int? = null, location: String? = null) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listThresholds(productId = productId, location = location, limit = 100, offset = 0)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful && res.body() != null) {
                    val pending = buildPendingThresholds()
                    items = pending + res.body()!!.items
                    val productNames = fetchProductNames(items.map { it.productId }.toSet())
                    val rows = items.map { ThresholdRowUi(it, productNames[it.productId]) }
                    adapter.submit(rows)
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code()))
                }
            } catch (e: Exception) {
                val pending = buildPendingThresholds()
                val productNames = fetchProductNames(pending.map { it.productId }.toSet())
                val rows = pending.map { ThresholdRowUi(it, productNames[it.productId]) }
                adapter.submit(rows)
                if (e is IOException) {
                    UiNotifier.show(this@ThresholdsActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@ThresholdsActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun createThreshold() {
        val productId = binding.etProductId.text.toString().trim().toIntOrNull()
        val location = binding.etLocation.text.toString().trim().ifBlank { null }
        val minQty = binding.etMinQty.text.toString().trim().toIntOrNull()

        if (productId == null) { binding.etProductId.error = "Product ID requerido"; return }
        if (minQty == null || minQty < 0) { binding.etMinQty.error = "Min >= 0"; return }

        binding.btnCreate.isEnabled = false
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createThreshold(
                    ThresholdCreateDto(productId = productId, location = location, minQuantity = minQty)
                )
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    binding.etProductId.setText("")
                    binding.etLocation.setText("")
                    binding.etMinQty.setText("")
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    val dto = ThresholdCreateDto(productId = productId, location = location, minQuantity = minQty)
                    OfflineQueue(this@ThresholdsActivity).enqueue(PendingType.THRESHOLD_CREATE, gson.toJson(dto))
                    UiNotifier.show(this@ThresholdsActivity, "Sin conexión. Threshold guardado offline")
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, "Error de red: ${e.message}")
                }
            } finally {
                binding.btnCreate.isEnabled = true
            }
        }
    }

    private fun showEditDialog(threshold: ThresholdResponseDto) {
        val inputLocation = android.widget.EditText(this).apply {
            hint = "Location"
            setText(threshold.location ?: "")
        }
        val inputMin = android.widget.EditText(this).apply {
            hint = "Min quantity"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(threshold.minQuantity.toString())
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(inputLocation)
            addView(inputMin)
            setPadding(32, 16, 32, 0)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar threshold #${threshold.id}")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Eliminar") { _, _ -> deleteThreshold(threshold.id) }
            .setPositiveButton("Guardar") { _, _ ->
                val newLoc = inputLocation.text.toString().trim().ifBlank { null }
                val newMin = inputMin.text.toString().trim().toIntOrNull()
                if (newMin == null || newMin < 0) {
                    UiNotifier.show(this, "Min >= 0")
                } else {
                    updateThreshold(threshold.id, newLoc, newMin)
                }
            }
            .show()
    }

    private fun updateThreshold(id: Int, location: String?, minQty: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateThreshold(
                    id,
                    ThresholdUpdateDto(location = location, minQuantity = minQty)
                )
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    UiNotifier.show(this@ThresholdsActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@ThresholdsActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun deleteThreshold(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.deleteThreshold(id)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    UiNotifier.show(this@ThresholdsActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@ThresholdsActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun buildPendingThresholds(): List<ThresholdResponseDto> {
        val pending = OfflineQueue(this).getAll().filter { it.type == PendingType.THRESHOLD_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, ThresholdCreateDto::class.java) }.getOrNull()
            ThresholdResponseDto(
                id = -1 - index,
                productId = dto?.productId ?: 0,
                locationId = null,
                location = dto?.location,
                minQuantity = dto?.minQuantity ?: 0,
                createdAt = "offline",
                updatedAt = null
            )
        }
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
