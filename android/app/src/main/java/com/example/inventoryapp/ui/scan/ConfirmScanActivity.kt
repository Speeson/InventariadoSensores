package com.example.inventoryapp.ui.scan

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.databinding.ActivityConfirmScanBinding
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType
import com.example.inventoryapp.data.repository.remote.RemoteScanRepository
import com.example.inventoryapp.data.repository.remote.ScanSendResult
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.movements.ResultActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.google.gson.Gson
import kotlinx.coroutines.launch

class ConfirmScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmScanBinding
    private val repo = RemoteScanRepository()
    private var selectedType: MovementType = MovementType.OUT
    private var productExists: Boolean = true
    private var isOfflineMode: Boolean = false
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        val snack = SendSnack(binding.root)
        val barcode = intent.getStringExtra("barcode").orEmpty()
        isOfflineMode = intent.getBooleanExtra("offline", false)
        binding.tvBarcode.text = "Barcode: $barcode"

        applyTypeSelection(MovementType.OUT)
        binding.btnTypeIn.setOnClickListener { applyTypeSelection(MovementType.IN) }
        binding.btnTypeOut.setOnClickListener { applyTypeSelection(MovementType.OUT) }

        setupLocationDropdown()

        if (isOfflineMode) {
            binding.tvProductName.text = "Sin conexion: validacion pendiente"
            binding.tvProductName.setTextColor(
                ContextCompat.getColor(this@ConfirmScanActivity, android.R.color.holo_orange_dark)
            )
            productExists = true
        } else {
            lifecycleScope.launch {
                try {
                    val res = NetworkModule.api.listProducts(barcode = barcode, limit = 1, offset = 0)
                    if (res.isSuccessful && res.body() != null && res.body()!!.items.isNotEmpty()) {
                        val p = res.body()!!.items.first()
                        binding.tvProductName.text = "${p.name} (SKU ${p.sku})"
                        productExists = true
                    } else {
                        binding.tvProductName.text = "Producto no encontrado"
                        binding.tvProductName.setTextColor(
                            ContextCompat.getColor(this@ConfirmScanActivity, android.R.color.holo_red_dark)
                        )
                        productExists = false
                        snack.showError("Producto no encontrado")
                    }
                } catch (_: Exception) {
                    isOfflineMode = true
                    binding.tvProductName.text = "Sin conexion: validacion pendiente"
                    binding.tvProductName.setTextColor(
                        ContextCompat.getColor(this@ConfirmScanActivity, android.R.color.holo_orange_dark)
                    )
                    productExists = true
                    snack.showError("Sin conexion. Se enviara cuando vuelvas a estar online")
                }
            }
        }

        binding.btnConfirm.setOnClickListener {
            if (!productExists && !isOfflineMode) {
                snack.showError("Producto no encontrado")
                return@setOnClickListener
            }

            val quantity = binding.etQuantity.text.toString().toIntOrNull() ?: 0
            if (quantity <= 0) {
                binding.etQuantity.error = "Cantidad > 0"
                return@setOnClickListener
            }

            val location = normalizeLocationInput(binding.etLocation.text.toString().trim()).ifEmpty { "default" }
            val movement = Movement(
                barcode = barcode,
                type = selectedType,
                quantity = quantity,
                location = location
            )

            binding.btnConfirm.isEnabled = false
            lifecycleScope.launch {
                try {
                    if (isOfflineMode) {
                        enqueueOffline(movement)
                        val i = Intent(this@ConfirmScanActivity, ResultActivity::class.java)
                        i.putExtra("success", true)
                        i.putExtra("msg", "Guardado offline. Se enviara al reconectar")
                        startActivity(i)
                    } else {
                        val result = repo.sendFromBarcode(movement)
                        val i = Intent(this@ConfirmScanActivity, ResultActivity::class.java)
                        if (result.isSuccess) {
                            i.putExtra("success", true)
                            val payload = result.getOrNull()
                            i.putExtra("msg", buildSendMessage(payload))
                            if (payload != null) {
                                i.putExtra("event_status", payload.status)
                                if (payload.eventId != null) {
                                    i.putExtra("event_id", payload.eventId)
                                }
                            }
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is java.io.IOException) {
                                enqueueOffline(movement)
                                i.putExtra("success", true)
                                i.putExtra("msg", "Guardado offline. Se enviara al reconectar")
                            } else {
                                i.putExtra("success", false)
                                i.putExtra("msg", error?.message ?: "Error")
                            }
                        }
                        startActivity(i)
                    }
                } catch (e: Exception) {
                    if (e is java.io.IOException) {
                        enqueueOffline(movement)
                        val i = Intent(this@ConfirmScanActivity, ResultActivity::class.java)
                        i.putExtra("success", true)
                        i.putExtra("msg", "Guardado offline. Se enviara al reconectar")
                        startActivity(i)
                    } else {
                        val i = Intent(this@ConfirmScanActivity, ResultActivity::class.java)
                        i.putExtra("success", false)
                        i.putExtra("msg", e.message ?: "Error")
                        startActivity(i)
                    }
                } finally {
                    binding.btnConfirm.isEnabled = true
                }
            }
        }
    }

    private fun enqueueOffline(movement: Movement) {
        val payload = OfflineSyncer.ScanEventPayload(
            barcode = movement.barcode,
            type = movement.type,
            quantity = movement.quantity,
            location = movement.location?.ifBlank { "default" } ?: "default",
            source = "SCAN"
        )
        OfflineQueue(this).enqueue(PendingType.SCAN_EVENT, gson.toJson(payload))
    }

    private fun setupLocationDropdown() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val values = items.map { "(${it.id}) ${it.code}" }.distinct().sorted()
                    val allValues = if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = ArrayAdapter(this@ConfirmScanActivity, android.R.layout.simple_list_item_1, allValues)
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

    private fun applyTypeSelection(type: MovementType) {
        selectedType = type
        val selectedBg = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
        val selectedText = ContextCompat.getColor(this, android.R.color.white)
        val normalBg = ContextCompat.getColor(this, android.R.color.darker_gray)
        val normalText = ContextCompat.getColor(this, android.R.color.black)

        val isIn = type == MovementType.IN
        binding.btnTypeIn.setBackgroundColor(if (isIn) selectedBg else normalBg)
        binding.btnTypeIn.setTextColor(if (isIn) selectedText else normalText)
        binding.btnTypeOut.setBackgroundColor(if (!isIn) selectedBg else normalBg)
        binding.btnTypeOut.setTextColor(if (!isIn) selectedText else normalText)
    }

    private fun buildSendMessage(payload: ScanSendResult?): String {
        if (payload == null) return "Evento enviado"
        val idPart = payload.eventId?.let { " (id=$it)" } ?: ""
        return "Evento enviado: ${payload.productName}$idPart"
    }
}
