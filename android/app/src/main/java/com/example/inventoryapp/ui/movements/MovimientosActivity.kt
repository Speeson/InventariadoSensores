package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementAdjustOperationRequest
import com.example.inventoryapp.data.remote.model.MovementOperationRequest
import com.example.inventoryapp.data.remote.model.MovementSourceDto
import com.example.inventoryapp.data.remote.model.MovementTransferOperationRequest
import com.example.inventoryapp.databinding.ActivityMovimientosBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class MovimientosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovimientosBinding
    private lateinit var snack: SendSnack
    private val gson = Gson()
    private var quantityHint: String = "Cantidad"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovimientosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        // Si tienes tvResult en el layout, lo dejamos vacio para evitar doble mensaje
        binding.tvResult.text = ""

        binding.btnSendMovement.setOnClickListener { sendMovement() }

        setupMovementTypeDropdown()
        setupSourceDropdown()
        setupLocationDropdown()
        setupQuantityFocusHint()
    }

    private fun sendMovement() {
        val type = binding.etMovementType.text.toString().trim().uppercase()
        val productId = binding.etProductId.text.toString().trim().toIntOrNull()
        val quantity = binding.etQuantity.text.toString().trim().toIntOrNull()
        val location = normalizeLocationInput(binding.etLocation.text.toString().trim()).ifBlank { "default" }
        val toLocation = normalizeLocationInput(binding.etToLocation.text.toString().trim())
        val sourceRaw = binding.etSource.text.toString().trim().uppercase()

        if (productId == null) { binding.etProductId.error = "Product ID requerido"; return }
        val source = when (sourceRaw) {
            "SCAN" -> MovementSourceDto.SCAN
            "MANUAL" -> MovementSourceDto.MANUAL
            else -> { binding.etSource.error = "Usa SCAN o MANUAL"; return }
        }

        // Validacion por tipo
        when (type) {
            "IN", "OUT" -> {
                if (quantity == null || quantity <= 0) { binding.etQuantity.error = "Cantidad > 0"; return }
            }
            "ADJUST" -> {
                if (quantity == null || quantity == 0) { binding.etQuantity.error = "Delta != 0"; return }
            }
            "TRANSFER" -> {
                if (quantity == null || quantity <= 0) { binding.etQuantity.error = "Cantidad > 0"; return }
                if (toLocation.isBlank()) { binding.etToLocation.error = "Ubicacion destino requerida"; return }
                if (location.equals(toLocation, ignoreCase = true)) {
                    binding.etToLocation.error = "Origen y destino no pueden ser iguales"
                    return
                }
            }
            else -> {
                binding.etMovementType.error = "Usa IN / OUT / ADJUST / TRANSFER"
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
                            snack.showSuccess("IN OK | stock=${body.stock.quantity} @ ${body.stock.location}")
                        } else {
                            if (res.code() == 403) {
                            UiNotifier.showBlocking(
                                this@MovimientosActivity,
                                "Permisos insuficientes",
                                "No tienes permisos para crear movimientos.",
                                com.example.inventoryapp.R.drawable.ic_lock
                            )
                        } else {
                            snack.showError("Error ${res.code()}: ${res.errorBody()?.string()}")
                        }
                        }
                    }

                    "OUT" -> {
                        val dto = MovementOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementOut(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            snack.showSuccess("OUT OK | stock=${body.stock.quantity} @ ${body.stock.location}")
                        } else {
                            if (res.code() == 403) {
                            UiNotifier.showBlocking(
                                this@MovimientosActivity,
                                "Permisos insuficientes",
                                "No tienes permisos para crear movimientos.",
                                com.example.inventoryapp.R.drawable.ic_lock
                            )
                        } else {
                            snack.showError("Error ${res.code()}: ${res.errorBody()?.string()}")
                        }
                        }
                    }

                    "ADJUST" -> {
                        val dto = MovementAdjustOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementAdjust(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            snack.showSuccess("ADJUST OK | stock=${body.stock.quantity} @ ${body.stock.location}")
                        } else {
                            if (res.code() == 403) {
                            UiNotifier.showBlocking(
                                this@MovimientosActivity,
                                "Permisos insuficientes",
                                "No tienes permisos para crear movimientos.",
                                com.example.inventoryapp.R.drawable.ic_lock
                            )
                        } else {
                            snack.showError("Error ${res.code()}: ${res.errorBody()?.string()}")
                        }
                        }
                    }

                    "TRANSFER" -> {
                        val dto = MovementTransferOperationRequest(productId, quantity!!, location, toLocation, source)
                        val res = NetworkModule.api.movementTransfer(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            snack.showSuccess(
                                "TRANSFER OK | ${body.fromStock.location}=${body.fromStock.quantity} -> " +
                                    "${body.toStock.location}=${body.toStock.quantity}"
                            )
                        } else {
                            if (res.code() == 403) {
                            UiNotifier.showBlocking(
                                this@MovimientosActivity,
                                "Permisos insuficientes",
                                "No tienes permisos para crear movimientos.",
                                com.example.inventoryapp.R.drawable.ic_lock
                            )
                        } else {
                            snack.showError("Error ${res.code()}: ${res.errorBody()?.string()}")
                        }
                        }
                    }
                }

            } catch (e: IOException) {
                // Encolar segun tipo
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
                        val dto = MovementAdjustOperationRequest(productId, quantity ?: 1, location, source)
                        OfflineQueue(this@MovimientosActivity).enqueue(PendingType.MOVEMENT_ADJUST, gson.toJson(dto))
                    }
                    "TRANSFER" -> {
                        val dto = MovementTransferOperationRequest(
                            productId,
                            quantity ?: 1,
                            location,
                            toLocation.ifBlank { location },
                            source
                        )
                        OfflineQueue(this@MovimientosActivity).enqueue(PendingType.MOVEMENT_TRANSFER, gson.toJson(dto))
                    }
                }
                snack.showQueuedOffline("Sin red/backend caido. Guardado offline para reenviar.")

            } catch (e: Exception) {
                snack.showError("Error: ${e.message}")
            } finally {
                binding.btnSendMovement.isEnabled = true
            }
        }
    }

    private fun setupMovementTypeDropdown() {
        val values = listOf("", "IN", "OUT", "ADJUST", "TRANSFER")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, values)
        binding.etMovementType.setAdapter(adapter)
        updateTransferVisibility("")
        updateQuantityForType("")
        updateLocationHints("")

        binding.etMovementType.setOnItemClickListener { _, _, position, _ ->
            if (values[position].isBlank()) {
                binding.etMovementType.setText("", false)
            }
        }
        binding.etMovementType.setOnClickListener { binding.etMovementType.showDropDown() }
        binding.etMovementType.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etMovementType.showDropDown()
        }
        binding.etMovementType.addTextChangedListener { text ->
            val type = text?.toString()?.trim()?.uppercase() ?: ""
            updateTransferVisibility(type)
            updateQuantityForType(type)
            updateLocationHints(type)
        }
    }

    private fun updateTransferVisibility(type: String) {
        binding.tilToLocation.visibility =
            if (type == "TRANSFER") android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateQuantityForType(type: String) {
        if (type == "ADJUST") {
            quantityHint = "Delta"
            binding.etQuantity.hint = quantityHint
            binding.etQuantity.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        } else {
            quantityHint = "Cantidad"
            binding.etQuantity.hint = quantityHint
            binding.etQuantity.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    private fun updateLocationHints(type: String) {
        if (type == "TRANSFER") {
            binding.etLocation.hint = "Ubicacion de origen"
            binding.tilToLocation.hint = "Ubicacion destino"
        } else {
            binding.etLocation.hint = "Ubicacion"
            binding.tilToLocation.hint = "Ubicacion destino"
        }
    }

    private fun setupQuantityFocusHint() {
        binding.etQuantity.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.etQuantity.hint = ""
            } else if (binding.etQuantity.text.isNullOrBlank()) {
                binding.etQuantity.hint = quantityHint
            }
        }
    }

    private fun setupSourceDropdown() {
        val values = listOf("", "MANUAL", "SCAN")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, values)
        binding.etSource.setAdapter(adapter)

        binding.etSource.setOnItemClickListener { _, _, position, _ ->
            if (values[position].isBlank()) {
                binding.etSource.setText("", false)
            }
        }
        binding.etSource.setOnClickListener { binding.etSource.showDropDown() }
        binding.etSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSource.showDropDown()
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
                    val adapter = ArrayAdapter(this@MovimientosActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etLocation.setAdapter(adapter)
                    binding.etToLocation.setAdapter(adapter)
                    binding.etLocation.setOnClickListener { binding.etLocation.showDropDown() }
                    binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etLocation.showDropDown()
                    }
                    binding.etToLocation.setOnClickListener { binding.etToLocation.showDropDown() }
                    binding.etToLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etToLocation.showDropDown()
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
