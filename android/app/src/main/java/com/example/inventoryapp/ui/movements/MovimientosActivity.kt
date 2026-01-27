package com.example.inventoryapp.ui.movements

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementAdjustOperationRequest
import com.example.inventoryapp.data.remote.model.MovementOperationRequest
import com.example.inventoryapp.data.remote.model.MovementSourceDto
import com.example.inventoryapp.databinding.ActivityMovimientosBinding
import com.example.inventoryapp.ui.common.SendSnack
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class MovimientosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovimientosBinding
    private lateinit var snack: SendSnack
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovimientosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Si tienes tvResult en el layout, lo dejamos vacío para evitar doble mensaje
        binding.tvResult.text = ""

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

        // Validación por tipo
        when (type) {
            "IN", "OUT" -> {
                if (quantity == null || quantity <= 0) { binding.etQuantity.error = "Cantidad > 0"; return }
            }
            "ADJUST" -> {
                if (delta == null || delta == 0) { binding.etDelta.error = "Delta != 0"; return }
            }
            else -> {
                binding.etMovementType.error = "Usa IN / OUT / ADJUST"
                return
            }
        }

        binding.btnSendMovement.isEnabled = false
        snack.showSending("Enviando movimiento...")

        lifecycleScope.launch {
            try {
                when (type) {
                    "IN" -> {
                        val dto = MovementOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementIn(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            snack.showSuccess("✅ IN OK | stock=${body.stock.quantity} @ ${body.stock.location}")
                        } else {
                            snack.showError("❌ Error ${res.code()}: ${res.errorBody()?.string()}")
                        }
                    }

                    "OUT" -> {
                        val dto = MovementOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementOut(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            snack.showSuccess("✅ OUT OK | stock=${body.stock.quantity} @ ${body.stock.location}")
                        } else {
                            snack.showError("❌ Error ${res.code()}: ${res.errorBody()?.string()}")
                        }
                    }

                    "ADJUST" -> {
                        val dto = MovementAdjustOperationRequest(productId, delta!!, location, source)
                        val res = NetworkModule.api.movementAdjust(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            snack.showSuccess("✅ ADJUST OK | stock=${body.stock.quantity} @ ${body.stock.location}")
                        } else {
                            snack.showError("❌ Error ${res.code()}: ${res.errorBody()?.string()}")
                        }
                    }
                }

            } catch (e: IOException) {
                // Encolar según tipo
                when (type) {
                    "IN" -> {
                        val dto = MovementOperationRequest(productId, quantity ?: 1, location, source)
                        OfflineQueue(this@MovimientosActivity).enqueue(PendingType.MOVEMENT_IN, gson.toJson(dto))
                    }
                    "OUT" -> {
                        val dto = MovementOperationRequest(productId, quantity ?: 1, location, source)
                        OfflineQueue(this@MovimientosActivity).enqueue(PendingType.MOVEMENT_OUT, gson.toJson(dto))
                    }
                    "ADJUST" -> {
                        val dto = MovementAdjustOperationRequest(productId, delta ?: 1, location, source)
                        OfflineQueue(this@MovimientosActivity).enqueue(PendingType.MOVEMENT_ADJUST, gson.toJson(dto))
                    }
                }
                snack.showQueuedOffline("📦 Sin red/backend caído. Guardado offline para reenviar ✅")

            } catch (e: Exception) {
                snack.showError("❌ Error: ${e.message}")
            } finally {
                binding.btnSendMovement.isEnabled = true
            }
        }
    }
}
