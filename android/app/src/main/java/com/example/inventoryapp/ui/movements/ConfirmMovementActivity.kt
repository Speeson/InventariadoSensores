package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.repository.remote.RemoteScanRepository
import com.example.inventoryapp.databinding.ActivityConfirmMovementBinding
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType
import com.example.inventoryapp.ui.common.ApiResultMapper
import com.example.inventoryapp.ui.common.UiResult
import kotlinx.coroutines.launch

class ConfirmMovementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmMovementBinding
    private val repo = RemoteScanRepository()

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
            val location = binding.etLocation.text.toString().trim().ifEmpty { "default" }

            val movement = Movement(
                barcode = barcode,
                type = type,
                quantity = quantity,
                location = location
            )

            binding.btnConfirm.isEnabled = false

            lifecycleScope.launch {
                val result = repo.sendFromBarcode(movement)

                val ui = if (result.isSuccess) {
                    UiResult.Success(result.getOrNull()!!)
                } else {
                    ApiResultMapper.fromException(result.exceptionOrNull()!!)
                }

                val i = Intent(this@ConfirmMovementActivity, ResultActivity::class.java)
                i.putExtra("success", ui is UiResult.Success)
                i.putExtra("msg", when (ui) {
                    is UiResult.Success -> ui.msg
                    is UiResult.Error -> ui.msg
                    else -> "Sesión caducada"
                })
                startActivity(i)
                binding.btnConfirm.isEnabled = true
            }
        }
    }
}
