package com.example.inventoryapp.ui.stock

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.StockCreateDto
import com.example.inventoryapp.data.remote.model.StockResponseDto
import com.example.inventoryapp.data.remote.model.StockUpdateDto
import com.example.inventoryapp.databinding.ActivityStockBinding
import com.example.inventoryapp.ui.common.SendSnack
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class StockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockBinding
    private lateinit var snack: SendSnack

    private val gson = Gson()
    private var items: List<StockResponseDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnCreate.setOnClickListener { createStock() }

        setupLocationDropdown()

        binding.lvStocks.setOnItemClickListener { _, _, position, _ ->
            showEditDialog(items[position])
        }
    }

    override fun onResume() {
        super.onResume()
        loadStocks()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadStocks() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listStocks()
                if (res.isSuccessful && res.body() != null) {
                    items = res.body()!!.items
                    val text = items.map { "(${it.id}) prod=${it.productId} loc=${it.location} qty=${it.quantity}" }
                    binding.lvStocks.adapter = ArrayAdapter(this@StockActivity, android.R.layout.simple_list_item_1, text)
                } else {
                    snack.showError("❌ Error ${res.code()}")
                }
            } catch (e: Exception) {
                snack.showError("❌ Error de red: ${e.message}")
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
                snack.showQueuedOffline("📦 Sin red. Stock guardado offline ✅")

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
                snack.showQueuedOffline("📦 Sin red. Update guardado offline ✅")

            } catch (e: Exception) {
                snack.showError("❌ Error red: ${e.message}")
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
