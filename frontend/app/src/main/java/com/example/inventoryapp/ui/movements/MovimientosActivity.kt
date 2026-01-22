package com.example.inventoryapp.ui.movements

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementAdjustOperationRequest
import com.example.inventoryapp.data.remote.model.MovementOperationRequest
import com.example.inventoryapp.data.remote.model.MovementSourceDto
import com.example.inventoryapp.databinding.ActivityMovimientosBinding
import kotlinx.coroutines.launch

class MovimientosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovimientosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovimientosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar + flecha
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnSendMovement.setOnClickListener {
            sendMovement()
        }
    }

    private fun sendMovement() {
        val type = binding.etMovementType.text.toString().trim().uppercase()
        val productId = binding.etProductId.text.toString().trim().toIntOrNull()
        val quantity = binding.etQuantity.text.toString().trim().toIntOrNull()
        val delta = binding.etDelta.text.toString().trim().toIntOrNull()
        val location = binding.etLocation.text.toString().trim()
        val sourceRaw = binding.etSource.text.toString().trim().uppercase()

        if (productId == null) {
            binding.etProductId.error = "Product ID requerido"
            return
        }
        if (location.isBlank()) {
            binding.etLocation.error = "Ubicación requerida"
            return
        }

        val source = when (sourceRaw) {
            "SCAN" -> MovementSourceDto.SCAN
            "MANUAL" -> MovementSourceDto.MANUAL
            else -> {
                binding.etSource.error = "Usa SCAN o MANUAL"
                return
            }
        }

        binding.btnSendMovement.isEnabled = false
        binding.tvResult.text = "Enviando..."

        lifecycleScope.launch {
            try {
                val res = when (type) {
                    "IN" -> {
                        if (quantity == null || quantity <= 0) {
                            binding.etQuantity.error = "Cantidad > 0"
                            binding.tvResult.text = ""
                            binding.btnSendMovement.isEnabled = true
                            return@launch
                        }
                        NetworkModule.api.movementIn(
                            MovementOperationRequest(productId, quantity, location, source)
                        )
                    }

                    "OUT" -> {
                        if (quantity == null || quantity <= 0) {
                            binding.etQuantity.error = "Cantidad > 0"
                            binding.tvResult.text = ""
                            binding.btnSendMovement.isEnabled = true
                            return@launch
                        }
                        NetworkModule.api.movementOut(
                            MovementOperationRequest(productId, quantity, location, source)
                        )
                    }

                    "ADJUST" -> {
                        if (delta == null || delta == 0) {
                            binding.etDelta.error = "Delta no puede ser 0"
                            binding.tvResult.text = ""
                            binding.btnSendMovement.isEnabled = true
                            return@launch
                        }
                        NetworkModule.api.movementAdjust(
                            MovementAdjustOperationRequest(productId, delta, location, source)
                        )
                    }

                    else -> {
                        binding.etMovementType.error = "Usa IN, OUT o ADJUST"
                        binding.tvResult.text = ""
                        binding.btnSendMovement.isEnabled = true
                        return@launch
                    }
                }

                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val msg = """
                        ✅ OK
                        Movement ID: ${body.movement.id}
                        Tipo: ${body.movement.movementType}
                        Producto: ${body.movement.productId}
                        Cantidad: ${body.movement.quantity}
                        Stock en ${body.stock.location}: ${body.stock.quantity}
                    """.trimIndent()

                    binding.tvResult.text = msg
                    Toast.makeText(this@MovimientosActivity, "Movimiento registrado ✅", Toast.LENGTH_SHORT).show()
                } else {
                    val err = res.errorBody()?.string()
                    binding.tvResult.text = "❌ Error ${res.code()}:\n${err ?: "sin detalle"}"
                    Toast.makeText(this@MovimientosActivity, "Error ${res.code()}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                binding.tvResult.text = "❌ Error de conexión:\n${e.message}"
                Toast.makeText(this@MovimientosActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSendMovement.isEnabled = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}