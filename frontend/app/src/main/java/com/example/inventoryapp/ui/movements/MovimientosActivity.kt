package com.example.inventoryapp.ui.movements

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementAdjustOperationRequest
import com.example.inventoryapp.data.remote.model.MovementOperationRequest
import com.example.inventoryapp.data.remote.model.MovementSourceDto
import com.example.inventoryapp.databinding.ActivityMovimientosBinding
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class MovimientosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovimientosBinding
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovimientosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSendMovement.setOnClickListener { sendMovement() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun sendMovement() {
        val type = binding.etMovementType.text.toString().trim().uppercase()
        val productId = binding.etProductId.text.toString().trim().toIntOrNull()
        val quantity = binding.etQuantity.text.toString().trim().toIntOrNull()
        val delta = binding.etDelta.text.toString().trim().toIntOrNull()
        val location = binding.etLocation.text.toString().trim().ifBlank { "default" }
        val sourceRaw = binding.etSource.text.toString().trim().uppercase()

        if (productId == null) { binding.etProductId.error = "Product ID requerido"; return }
        val source = when (sourceRaw) {
            "SCAN" -> MovementSourceDto.SCAN
            "MANUAL" -> MovementSourceDto.MANUAL
            else -> { binding.etSource.error = "Usa SCAN o MANUAL"; return }
        }

        binding.btnSendMovement.isEnabled = false
        binding.tvResult.text = "Enviando..."

        lifecycleScope.launch {
            try {
                when (type) {
                    "IN" -> {
                        if (quantity == null || quantity <= 0) { binding.etQuantity.error = "Cantidad > 0"; return@launch }
                        val dto = MovementOperationRequest(productId, quantity, location, source)
                        val res = NetworkModule.api.movementIn(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            binding.tvResult.text = "✅ IN OK | stock=${body.stock.quantity} @ ${body.stock.location}"
                        } else {
                            binding.tvResult.text = "❌ Error ${res.code()}: ${res.errorBody()?.string()}"
                        }
                    }

                    "OUT" -> {
                        if (quantity == null || quantity <= 0) { binding.etQuantity.error = "Cantidad > 0"; return@launch }
                        val dto = MovementOperationRequest(productId, quantity, location, source)
                        val res = NetworkModule.api.movementOut(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            binding.tvResult.text = "✅ OUT OK | stock=${body.stock.quantity} @ ${body.stock.location}"
                        } else {
                            binding.tvResult.text = "❌ Error ${res.code()}: ${res.errorBody()?.string()}"
                        }
                    }

                    "ADJUST" -> {
                        if (delta == null || delta == 0) { binding.etDelta.error = "Delta != 0"; return@launch }
                        val dto = MovementAdjustOperationRequest(productId, delta, location, source)
                        val res = NetworkModule.api.movementAdjust(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            binding.tvResult.text = "✅ ADJUST OK | stock=${body.stock.quantity} @ ${body.stock.location}"
                        } else {
                            binding.tvResult.text = "❌ Error ${res.code()}: ${res.errorBody()?.string()}"
                        }
                    }

                    else -> {
                        binding.etMovementType.error = "Usa IN / OUT / ADJUST"
                    }
                }

            } catch (e: IOException) {
                // Encolar según tipo
                when (type) {
                    "IN" -> {
                        val dto = MovementOperationRequest(productId!!, quantity ?: 1, location, source)
                        OfflineQueue(this@MovimientosActivity).enqueue(PendingType.MOVEMENT_IN, gson.toJson(dto))
                    }
                    "OUT" -> {
                        val dto = MovementOperationRequest(productId!!, quantity ?: 1, location, source)
                        OfflineQueue(this@MovimientosActivity).enqueue(PendingType.MOVEMENT_OUT, gson.toJson(dto))
                    }
                    "ADJUST" -> {
                        val dto = MovementAdjustOperationRequest(productId!!, delta ?: 1, location, source)
                        OfflineQueue(this@MovimientosActivity).enqueue(PendingType.MOVEMENT_ADJUST, gson.toJson(dto))
                    }
                }
                binding.tvResult.text = "📦 Guardado offline para reenviar ✅"
                Toast.makeText(this@MovimientosActivity, "Backend caído/sin red. Guardado offline ✅", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                binding.tvResult.text = "❌ Error: ${e.message}"
            } finally {
                binding.btnSendMovement.isEnabled = true
            }
        }
    }
}
