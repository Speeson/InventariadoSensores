package com.example.inventoryapp.ui.scan

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.databinding.ActivityConfirmScanBinding
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType
import com.example.inventoryapp.data.repository.remote.RemoteScanRepository
import com.example.inventoryapp.ui.movements.ResultActivity
import com.example.inventoryapp.ui.common.SendSnack
import kotlinx.coroutines.launch

class ConfirmScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmScanBinding
    private val repo = RemoteScanRepository()
    private var selectedType: MovementType = MovementType.OUT
    private var productExists: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val snack = SendSnack(binding.root)
        val barcode = intent.getStringExtra("barcode").orEmpty()
        binding.tvBarcode.text = "Barcode: $barcode"

        applyTypeSelection(MovementType.OUT)
        binding.btnTypeIn.setOnClickListener { applyTypeSelection(MovementType.IN) }
        binding.btnTypeOut.setOnClickListener { applyTypeSelection(MovementType.OUT) }

        setupLocationDropdown()

        lifecycleScope.launch {
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
        }

        binding.btnConfirm.setOnClickListener {
            if (!productExists) {
                snack.showError("Producto no encontrado")
                return@setOnClickListener
            }

            val quantity = binding.etQuantity.text.toString().toIntOrNull() ?: 0
            if (quantity <= 0) {
                binding.etQuantity.error = "Cantidad > 0"
                return@setOnClickListener
            }

            val location = binding.etLocation.text.toString().trim().ifEmpty { "default" }
            val movement = Movement(
                barcode = barcode,
                type = selectedType,
                quantity = quantity,
                location = location
            )

            binding.btnConfirm.isEnabled = false
            lifecycleScope.launch {
                val result = repo.sendFromBarcode(movement)
                val i = Intent(this@ConfirmScanActivity, ResultActivity::class.java)
                if (result.isSuccess) {
                    i.putExtra("success", true)
                    i.putExtra("msg", result.getOrNull() ?: "Evento OK")
                } else {
                    i.putExtra("success", false)
                    i.putExtra("msg", result.exceptionOrNull()?.message ?: "Error")
                }
                startActivity(i)
                binding.btnConfirm.isEnabled = true
            }
        }
    }

    private fun setupLocationDropdown() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val codes = res.body()!!.items.map { it.code }.distinct().sorted()
                    val values = if (codes.contains("default")) codes else listOf("default") + codes
                    val adapter = ArrayAdapter(this@ConfirmScanActivity, android.R.layout.simple_list_item_1, values)
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
}
