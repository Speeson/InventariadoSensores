package com.example.inventoryapp.ui.stock

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.StockCreateDto
import com.example.inventoryapp.data.remote.model.StockResponseDto
import com.example.inventoryapp.data.remote.model.StockUpdateDto
import com.example.inventoryapp.databinding.ActivityStockBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class StockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockBinding
    private lateinit var snack: SendSnack

    private val gson = Gson()
    private var items: List<StockResponseDto> = emptyList()
    private lateinit var adapter: StockListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnCreate.setOnClickListener { createStock() }

        setupLocationDropdown()

        adapter = StockListAdapter { stock -> showEditDialog(stock) }
        binding.rvStocks.layoutManager = LinearLayoutManager(this)
        binding.rvStocks.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadStocks()
    }

    private fun loadStocks() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listStocks()
                if (res.isSuccessful && res.body() != null) {
                    val pending = buildPendingStocks()
                    items = pending + res.body()!!.items
                    adapter.submit(items)
                } else {
                    val pending = buildPendingStocks()
                    adapter.submit(pending)
                    snack.showError("❌ Error ${res.code()}")
                }
            } catch (e: Exception) {
                val pending = buildPendingStocks()
                adapter.submit(pending)
                if (e is IOException) {
                    snack.showError("Sin conexión a Internet")
                } else {
                    snack.showError("❌ Error de red: ${e.message}")
                }
            }
        }
    }

    private fun createStock() {
        val productId = binding.etProductId.text.toString().toIntOrNull()
        val location = normalizeLocationInput(binding.etLocation.text.toString().trim())
        val quantity = binding.etQuantity.text.toString().toIntOrNull()

        if (productId == null) { binding.etProductId.error = "Product ID requerido"; return }
        if (location.isBlank()) { binding.etLocation.error = "Location requerida"; return }
        if (quantity == null || quantity < 0) { binding.etQuantity.error = "Quantity >= 0"; return }

        val dto = StockCreateDto(productId = productId, location = location, quantity = quantity)

        binding.btnCreate.isEnabled = false
        snack.showSending("Enviando stock...")

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createStock(dto)
                if (res.isSuccessful) {
                    snack.showSuccess("✅ Stock creado")
                    binding.etQuantity.setText("")
                    loadStocks()
                } else {
                    snack.showError("❌ Error ${res.code()}: ${res.errorBody()?.string()}")
                }

            } catch (e: IOException) {
                OfflineQueue(this@StockActivity).enqueue(PendingType.STOCK_CREATE, gson.toJson(dto))
                snack.showQueuedOffline("Sin conexión. Stock guardado offline")
                loadStocks()

            } catch (e: Exception) {
                snack.showError("❌ Error: ${e.message}")
            } finally {
                binding.btnCreate.isEnabled = true
            }
        }
    }

    private fun showEditDialog(stock: StockResponseDto) {
        val inputQty = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(stock.quantity.toString())
            hint = "Nueva quantity"
        }

        AlertDialog.Builder(this)
            .setTitle("Editar stock #${stock.id}")
            .setMessage("prod=${stock.productId} | loc=${stock.location}\nCambia quantity:")
            .setView(inputQty)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val newQty = inputQty.text.toString().toIntOrNull()
                if (newQty == null || newQty < 0) {
                    snack.showError("❌ Quantity inválida")
                    return@setPositiveButton
                }
                updateStock(stock.id, StockUpdateDto(quantity = newQty))
            }
            .show()
    }

    private fun updateStock(stockId: Int, body: StockUpdateDto) {
        snack.showSending("Enviando actualización de stock...")

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateStock(stockId, body)
                if (res.isSuccessful) {
                    snack.showSuccess("✅ Stock actualizado")
                    loadStocks()
                } else {
                    snack.showError("❌ Error ${res.code()}: ${res.errorBody()?.string()}")
                }

            } catch (e: IOException) {
                val payload = OfflineSyncer.StockUpdatePayload(stockId, body)
                OfflineQueue(this@StockActivity).enqueue(PendingType.STOCK_UPDATE, gson.toJson(payload))
                snack.showQueuedOffline("Sin conexión. Update guardado offline")

            } catch (e: Exception) {
                snack.showError("❌ Error red: ${e.message}")
            }
        }
    }

    private fun buildPendingStocks(): List<StockResponseDto> {
        val pending = OfflineQueue(this).getAll().filter { it.type == PendingType.STOCK_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, StockCreateDto::class.java) }.getOrNull()
            if (dto == null) {
                StockResponseDto(
                    productId = 0,
                    location = "offline",
                    quantity = 0,
                    id = -1 - index,
                    createdAt = "offline",
                    updatedAt = "offline"
                )
            } else {
                StockResponseDto(
                    productId = dto.productId,
                    location = dto.location,
                    quantity = dto.quantity,
                    id = -1 - index,
                    createdAt = "offline",
                    updatedAt = "offline"
                )
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
                    val adapter = ArrayAdapter(this@StockActivity, android.R.layout.simple_list_item_1, allValues)
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

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }
}
