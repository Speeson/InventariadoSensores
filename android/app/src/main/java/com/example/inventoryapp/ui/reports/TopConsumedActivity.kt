package com.example.inventoryapp.ui.reports

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityTopConsumedBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import kotlinx.coroutines.launch

class TopConsumedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTopConsumedBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack
    private lateinit var adapter: TopConsumedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTopConsumedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)
        session = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = TopConsumedAdapter(emptyList())
        binding.rvTopConsumed.layoutManager = LinearLayoutManager(this)
        binding.rvTopConsumed.adapter = adapter

        binding.btnRefreshTop.setOnClickListener { loadTopConsumed(withSnack = true) }
    }

    override fun onResume() {
        super.onResume()
        loadTopConsumed(withSnack = false)
    }

    private fun loadTopConsumed(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando top consumidos...")

        val limit = intent.getStringExtra(ReportsActivity.EXTRA_LIMIT)?.toIntOrNull() ?: 10
        val dateFrom = intent.getStringExtra(ReportsActivity.EXTRA_DATE_FROM)?.ifBlank { null }
        val dateTo = intent.getStringExtra(ReportsActivity.EXTRA_DATE_TO)?.ifBlank { null }
        val location = intent.getStringExtra(ReportsActivity.EXTRA_LOCATION)?.ifBlank { null }

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getTopConsumedReport(
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
                    snack.showError("Permiso denegado. Permisos insuficientes.")
                    return@launch
                }
                if (!res.isSuccessful || res.body() == null) {
                    snack.showError("Error reporte top consumidos: HTTP ${res.code()}")
                    return@launch
                }

                adapter.submit(res.body()!!.items)
                if (withSnack) snack.showSuccess("OK: Top consumidos cargado")
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
