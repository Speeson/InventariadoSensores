package com.example.inventoryapp.ui.stock

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
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
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class StockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockBinding
    private val gson = Gson()
    private var items: List<StockResponseDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnCreate.setOnClickListener { createStock() }

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
                    Toast.makeText(this@StockActivity, "Error ${res.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StockActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createStock() {
        val productId = binding.etProductId.text.toString().toIntOrNull()
        val location = binding.etLocation.text.toString().trim()
        val quantity = binding.etQuantity.text.toString().toIntOrNull()

        if (productId == null) { binding.etProductId.error = "Product ID requerido"; return }
        if (location.isBlank()) { binding.etLocation.error = "Location requerida"; return }
        if (quantity == null || quantity < 0) { binding.etQuantity.error = "Quantity >= 0"; return }

        val dto = StockCreateDto(productId = productId, location = location, quantity = quantity)

        binding.btnCreate.isEnabled = false

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createStock(dto)
                if (res.isSuccessful) {
                    Toast.makeText(this@StockActivity, "Stock creado ✅", Toast.LENGTH_SHORT).show()
                    binding.etQuantity.setText("")
                    loadStocks()
                } else {
                    Toast.makeText(this@StockActivity, "Error ${res.code()}: ${res.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }

            } catch (e: IOException) {
                OfflineQueue(this@StockActivity).enqueue(PendingType.STOCK_CREATE, gson.toJson(dto))
                Toast.makeText(this@StockActivity, "Sin red. Stock guardado offline ✅", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(this@StockActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "Quantity inválida", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                updateStock(stock.id, StockUpdateDto(quantity = newQty))
            }
            .show()
    }

    private fun updateStock(stockId: Int, body: StockUpdateDto) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateStock(stockId, body)
                if (res.isSuccessful) {
                    Toast.makeText(this@StockActivity, "Actualizado ✅", Toast.LENGTH_SHORT).show()
                    loadStocks()
                } else {
                    Toast.makeText(this@StockActivity, "Error ${res.code()}: ${res.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }

            } catch (e: IOException) {
                val payload = OfflineSyncer.StockUpdatePayload(stockId, body)
                OfflineQueue(this@StockActivity).enqueue(PendingType.STOCK_UPDATE, gson.toJson(payload))
                Toast.makeText(this@StockActivity, "Sin red. Update guardado offline ✅", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(this@StockActivity, "Error red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
