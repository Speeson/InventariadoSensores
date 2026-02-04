package com.example.inventoryapp.ui.reports

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityTurnoverReportBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.reports.TurnoverAdapter
import com.example.inventoryapp.ui.reports.TurnoverRow
import kotlinx.coroutines.launch

class TurnoverReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTurnoverReportBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack
    private lateinit var adapter: TurnoverAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTurnoverReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)
        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = TurnoverAdapter(emptyList())
        binding.rvTurnover.layoutManager = LinearLayoutManager(this)
        binding.rvTurnover.adapter = adapter

        binding.btnRefreshTurnover.setOnClickListener { loadTurnover(withSnack = true) }
    }

    override fun onResume() {
        super.onResume()
        loadTurnover(withSnack = false)
    }

    private fun loadTurnover(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando indice de rotacion...")

        val limit = intent.getStringExtra(ReportsActivity.EXTRA_LIMIT)?.toIntOrNull() ?: 10
        val dateFrom = intent.getStringExtra(ReportsActivity.EXTRA_DATE_FROM)?.ifBlank { null }
        val dateTo = intent.getStringExtra(ReportsActivity.EXTRA_DATE_TO)?.ifBlank { null }
        val location = intent.getStringExtra(ReportsActivity.EXTRA_LOCATION)?.ifBlank { null }

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getTurnoverReport(
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    location = location,
                    limit = limit,
                    offset = 0
                )

                if (res.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }
                if (res.code() == 403) {
                    com.example.inventoryapp.ui.common.UiNotifier.showBlocking(
                        this@TurnoverReportActivity,
                        "Permisos insuficientes",
                        "No tienes permisos para ver este reporte.",
                        com.example.inventoryapp.R.drawable.ic_lock
                    )
                    return@launch
                }
                if (!res.isSuccessful || res.body() == null) {
                    snack.showError("Error reporte rotacion: HTTP ${res.code()}")
                    return@launch
                }

                val rows = res.body()!!.items.map { item ->
                    TurnoverRow(
                        productId = item.productId,
                        sku = item.sku,
                        name = item.name,
                        outs = item.outs,
                        stockInitial = item.stockInitial,
                        stockFinal = item.stockFinal,
                        stockAverage = item.stockAverage,
                        turnover = item.turnover
                    )
                }.sortedByDescending { it.turnover ?: -1.0 }

                adapter.submit(rows)
                if (withSnack) snack.showSuccess("OK: Rotacion cargada")
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
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
