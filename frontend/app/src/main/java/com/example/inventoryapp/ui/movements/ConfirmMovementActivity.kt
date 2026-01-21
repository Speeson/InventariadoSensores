package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.repository.fake.FakeMovementRepository
import com.example.inventoryapp.databinding.ActivityConfirmMovementBinding
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType
import kotlinx.coroutines.launch

class ConfirmMovementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmMovementBinding
    private val repo = FakeMovementRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmMovementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val barcode = intent.getStringExtra("barcode").orEmpty()
        binding.tvBarcode.text = "Código: $barcode"

        binding.btnConfirm.setOnClickListener {
            val quantity = binding.etQuantity.text.toString().toIntOrNull() ?: 0
            if (quantity <= 0) {
                binding.etQuantity.error = "Cantidad > 0"
                return@setOnClickListener
            }

            val type = if (binding.rbIn.isChecked) MovementType.IN else MovementType.OUT
            val location = binding.etLocation.text.toString().trim().ifEmpty { null }

            val movement = Movement(
                barcode = barcode,
                type = type,
                quantity = quantity,
                location = location
            )

            lifecycleScope.launch {
                val result = repo.sendMovement(movement)
                val i = Intent(this@ConfirmMovementActivity, ResultActivity::class.java)
                if (result.isSuccess) {
                    i.putExtra("success", true)
                    i.putExtra("msg", "Movimiento enviado (FAKE)")
                } else {
                    i.putExtra("success", false)
                    i.putExtra("msg", result.exceptionOrNull()?.message ?: "Error")
                }
                startActivity(i)
            }
        }
    }
}
