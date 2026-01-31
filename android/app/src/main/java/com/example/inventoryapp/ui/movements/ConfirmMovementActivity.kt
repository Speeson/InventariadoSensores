package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.databinding.ActivityResultBinding
import com.example.inventoryapp.ui.ScanActivity

class ConfirmMovementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ==========================
        // ✅ TC-79
        // Leemos el estado del movimiento que viene del Intent
        // (PENDING / PROCESSED / ERROR)
        // ==========================
        val status = intent.getStringExtra("status") ?: "ERROR"

        // Mensaje descriptivo del resultado
        val msg = intent.getStringExtra("msg").orEmpty()

        // ==========================
        // Título según el estado
        // ==========================
        binding.tvTitle.text = when (status) {
            "PENDING" -> "⏳ Pendiente"
            "PROCESSED" -> "✅ Procesado"
            else -> "❌ Error"
        }

        // Mostramos el mensaje informativo
        binding.tvMsg.text = msg

        // ==========================
        // Botón volver al escáner
        // ==========================
        binding.btnBack.setOnClickListener {
            val i = Intent(this, ScanActivity::class.java)

            // Limpia la pila para no volver atrás a pantallas intermedias
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            startActivity(i)
        }
    }
}
