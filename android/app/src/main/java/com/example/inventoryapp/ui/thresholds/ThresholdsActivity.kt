package com.example.inventoryapp.ui.thresholds

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ThresholdCreateDto
import com.example.inventoryapp.data.remote.model.ThresholdResponseDto
import com.example.inventoryapp.data.remote.model.ThresholdUpdateDto
import com.example.inventoryapp.databinding.ActivityThresholdsBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class ThresholdsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdsBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: ThresholdListAdapter
    private var items: List<ThresholdResponseDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }

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
                    items = res.body()!!.items
                    adapter.submit(items)
                } else {
                    Toast.makeText(this@ThresholdsActivity, "Error ${res.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ThresholdsActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@ThresholdsActivity, "Error ${res.code()}: ${res.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ThresholdsActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnCreate.isEnabled = true
            }
        }
    }

    private fun showEditDialog(threshold: ThresholdResponseDto) {
        val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
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
                    Toast.makeText(this, "Min >= 0", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@ThresholdsActivity, "Error ${res.code()}: ${res.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ThresholdsActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@ThresholdsActivity, "Error ${res.code()}: ${res.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ThresholdsActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
