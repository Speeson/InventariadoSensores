package com.example.inventoryapp.ui.thresholds

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.model.LowStockDto
import com.example.inventoryapp.data.repository.ThresholdRepository
import com.example.inventoryapp.databinding.ActivityThresholdsBinding
import com.example.inventoryapp.ui.common.SendSnack
import kotlinx.coroutines.launch

class ThresholdsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdsBinding
    private lateinit var snack: SendSnack
    private lateinit var session: SessionManager

    private val repo = ThresholdRepository()
    private var items: List<LowStockDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)
        session = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        load()
    }

    private fun load() {
        snack.showSending("Cargando stock bajo...")

        lifecycleScope.launch {
            val res = repo.listLowStocks()
            if (res.isSuccess) {
                items = res.getOrNull()!!
                binding.lvThresholds.adapter = LowStockAdapter(
                    this@ThresholdsActivity,
                    items,
                    onEdit = { edit(it) }
                )
                snack.showSuccess("Listado actualizado")
            } else {
                snack.showError("No se pudo cargar el stock bajo")
            }
        }
    }

    private fun edit(item: LowStockDto) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Nueva cantidad"
        }

        AlertDialog.Builder(this)
            .setTitle("Actualizar stock")
            .setMessage("${item.productName} (${item.location})")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val qty = input.text.toString().toIntOrNull()
                if (qty != null && qty >= item.threshold) {
                    update(item.stockId, qty)
                } else {
                    snack.showError("La cantidad debe ser ≥ umbral")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun update(stockId: Int, qty: Int) {
        snack.showSending("Actualizando stock...")

        lifecycleScope.launch {
            val res = repo.updateStock(stockId, qty)
            if (res.isSuccess) {
                snack.showSuccess("Stock actualizado")
                load()
            } else {
                snack.showError("Error al actualizar stock")
            }
        }
    }
}