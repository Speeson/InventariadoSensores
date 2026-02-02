package com.example.inventoryapp.ui.rotation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityRotationBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import kotlinx.coroutines.launch

class RotationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRotationBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack
    private lateinit var adapter: RotationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRotationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)
        session = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = RotationAdapter(emptyList())
        binding.rvRotation.layoutManager = LinearLayoutManager(this)
        binding.rvRotation.adapter = adapter

        binding.btnRefreshRotation.setOnClickListener { loadRotation(withSnack = true) }
    }

    override fun onResume() {
        super.onResume()
        ensureRotationAccessThenLoad()
    }

    private fun ensureRotationAccessThenLoad() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.me()
                if (res.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    val role = res.body()!!.role
                    if (role == "MANAGER" || role == "ADMIN") {
                        loadRotation(withSnack = false)
                    } else {
                        snack.showError("Permiso denegado. Permisos insuficientes.")
                        finish()
                    }
                } else {
                    snack.showError("No se pudo validar permisos (${res.code()}).")
                    finish()
                }
            } catch (e: Exception) {
                snack.showError("Error de conexión: ${e.message}")
                finish()
            }
        }
    }

    private fun loadRotation(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando rotacion...")

        lifecycleScope.launch {
            try {
                val reportRes = NetworkModule.api.getTurnoverReport(limit = 100, offset = 0)

                if (reportRes.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (reportRes.code() == 403) {
                    snack.showError("Permiso denegado. Permisos insuficientes.")
                    return@launch
                }

                if (!reportRes.isSuccessful || reportRes.body() == null) {
                    snack.showError("Error reporte rotacion: HTTP ${reportRes.code()}")
                    return@launch
                }

                val rows = reportRes.body()!!.items.map { item ->
                    RotationRow(
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
