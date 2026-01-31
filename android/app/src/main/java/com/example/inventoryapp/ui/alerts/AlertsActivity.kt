package com.example.inventoryapp.ui.alerts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.model.AlertDto
import com.example.inventoryapp.data.repository.remote.AlertRepository
import com.example.inventoryapp.databinding.ActivityAlertsBinding
import com.example.inventoryapp.ui.common.SendSnack
import kotlinx.coroutines.launch

class AlertsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertsBinding
    private lateinit var snack: SendSnack
    private lateinit var session: SessionManager

    private val repo = AlertRepository()
    private var allAlerts: List<AlertDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)
        session = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnFilterAll.setOnClickListener { render(allAlerts) }
        binding.btnFilterPending.setOnClickListener {
            render(allAlerts.filter { it.status == "pending" })
        }
        binding.btnFilterDone.setOnClickListener {
            render(allAlerts.filter { it.status == "processed" })
        }

        loadAlerts()
    }

    private fun loadAlerts() {
        snack.showSending("Cargando alertas...")

        lifecycleScope.launch {
            val result = repo.listAlerts()

            if (result.isSuccess) {
                allAlerts = result.getOrNull()!!
                render(allAlerts)
                snack.showSuccess("Alertas cargadas")
            } else {
                snack.showError("No se pudieron cargar las alertas")
            }
        }
    }

    private fun render(list: List<AlertDto>) {
        binding.lvAlerts.adapter = AlertAdapter(
            this,
            list,
            onAck = { ackAlert(it) }
        )
    }

    private fun ackAlert(alert: AlertDto) {
        snack.showSending("Marcando alerta...")

        lifecycleScope.launch {
            val res = repo.ackAlert(alert.id)
            if (res.isSuccess) {
                snack.showSuccess("Alerta marcada como revisada")
                loadAlerts()
            } else {
                snack.showError("No se pudo marcar la alerta")
            }
        }
    }
}