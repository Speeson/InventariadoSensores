package com.example.inventoryapp.ui.rotation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementTypeDto
import com.example.inventoryapp.databinding.ActivityRotationBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
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

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

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
                val movRes = NetworkModule.api.listMovements(limit = 100, offset = 0)
                val prodRes = NetworkModule.api.listProducts(limit = 100, offset = 0)

                if (movRes.code() == 401 || prodRes.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (movRes.code() == 403 || prodRes.code() == 403) {
                    snack.showError("Permiso denegado. Permisos insuficientes.")
                    return@launch
                }

                if (!movRes.isSuccessful || movRes.body() == null) {
                    snack.showError("Error movimientos: HTTP ${movRes.code()}")
                    return@launch
                }
                if (!prodRes.isSuccessful || prodRes.body() == null) {
                    snack.showError("Error productos: HTTP ${prodRes.code()}")
                    return@launch
                }

                val products = prodRes.body()!!.items.associateBy { it.id }
                val movements = movRes.body()!!.items

                // Agrupar por transfer_id (solo transferencias)
                val grouped = movements
                    .filter { !it.transferId.isNullOrBlank() }
                    .groupBy { it.transferId!! }

                val rows = grouped.values.mapNotNull { group ->
                    val outMov = group.firstOrNull { it.movementType == MovementTypeDto.OUT }
                    val inMov = group.firstOrNull { it.movementType == MovementTypeDto.IN }
                    val base = outMov ?: inMov ?: return@mapNotNull null
                    val product = products[base.productId]

                    RotationRow(
                        productId = base.productId,
                        sku = product?.sku ?: "SKU-${base.productId}",
                        name = product?.name ?: "Producto ${base.productId}",
                        quantity = outMov?.quantity ?: inMov?.quantity ?: base.quantity,
                        fromLocation = outMov?.location ?: "N/A",
                        toLocation = inMov?.location ?: "N/A",
                        createdAt = base.createdAt
                    )
                }.sortedByDescending { it.createdAt }

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
