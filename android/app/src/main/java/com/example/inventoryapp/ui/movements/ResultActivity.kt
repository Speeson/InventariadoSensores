package com.example.inventoryapp.ui.movements
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.databinding.ActivityResultBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.scan.ScanActivity
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
val success = intent.getBooleanExtra("success", false)
        val msg = intent.getStringExtra("msg").orEmpty()
        val eventStatus = intent.getStringExtra("event_status")

        binding.tvTitle.text = if (success && eventStatus != null) "Evento enviado" else if (success) "Exito" else "Error"
        binding.tvMsg.text = msg

        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        if (success && eventStatus != null) {
            val statusMsg = when (eventStatus) {
                "PROCESSED" -> "Evento procesado correctamente."
                "FAILED" -> "El evento fallo al procesarse."
                else -> "Evento enviado. Aun pendiente de procesamiento."
            }
            AlertDialog.Builder(this)
                .setTitle("Estado del evento")
                .setMessage(statusMsg)
                .setPositiveButton("OK", null)
                .show()
        }

        binding.btnBack.setOnClickListener {
            val i = Intent(this, ScanActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        }
    }
}
