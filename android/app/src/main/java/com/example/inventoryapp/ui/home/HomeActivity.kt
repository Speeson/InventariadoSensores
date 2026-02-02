package com.example.inventoryapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityHomeBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.events.EventsActivity
import com.example.inventoryapp.ui.movements.MovimientosActivity
import com.example.inventoryapp.ui.products.ProductListActivity
import com.example.inventoryapp.ui.scan.ScanActivity
import com.example.inventoryapp.ui.stock.StockActivity
import com.example.inventoryapp.ui.rotation.RotationActivity
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.offline.OfflineErrorsActivity


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // ✅ Si no hay token, fuera
        if (session.getToken().isNullOrBlank()) {
            goToLogin()
            return
        }

        setSupportActionBar(binding.toolbar)

        // Pop-up bienvenida si venías de registro
        intent.getStringExtra("welcome_email")?.takeIf { it.isNotBlank() }?.let { email ->
            AlertDialog.Builder(this)
                .setTitle("Bienvenido")
                .setMessage("¡Bienvenido, $email!")
                .setPositiveButton("OK", null)
                .show()
        }

        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        binding.btnProducts.setOnClickListener {
            startActivity(Intent(this, ProductListActivity::class.java))
        }

        binding.btnStock.setOnClickListener {
            startActivity(Intent(this, StockActivity::class.java))
        }

        binding.btnMovements.setOnClickListener {
            startActivity(Intent(this, MovimientosActivity::class.java))
        }

        binding.btnEvents.setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }

        binding.btnRotation.setOnClickListener {
            startActivity(Intent(this, RotationActivity::class.java))
        }

        binding.btnOfflineErrors.setOnClickListener {
            startActivity(Intent(this, OfflineErrorsActivity::class.java))
        }

    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            ensureValidSession()

            val report = OfflineSyncer.flush(this@HomeActivity)

            if (report.sent > 0) {
                Toast.makeText(
                    this@HomeActivity,
                    "Reenviados ${report.sent} pendientes ✅",
                    Toast.LENGTH_LONG
                ).show()
            }

            if (report.movedToFailed > 0) {
                Toast.makeText(
                    this@HomeActivity,
                    "${report.movedToFailed} pendientes con error ❗ Revisa 'Pendientes'",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_system_status -> { showSystemStatus(); true }
            R.id.action_profile -> { showProfile(); true }
            R.id.action_logout -> { confirmLogout(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSystemStatus() {
        lifecycleScope.launch {
            val q = OfflineQueue(this@HomeActivity)
            val pending = q.size()
            val failed = q.getFailed().size

            try {
                val res = NetworkModule.api.health()
                if (res.isSuccessful) {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Estado del sistema")
                        .setMessage(
                            "Backend OK ✅\n" +
                                    "Pendientes offline: $pending\n" +
                                    "Pendientes con error: $failed"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val detail = try { res.errorBody()?.string()?.take(200) } catch (_: Exception) { null }
                    val detailMsg = if (!detail.isNullOrBlank()) "\n$detail" else ""
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Estado del sistema")
                        .setMessage(
                            "Backend respondió ${res.code()} ❌\n" +
                                    "Pendientes offline: $pending\n" +
                                    "Pendientes con error: $failed"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("Estado del sistema")
                    .setMessage(
                        "No se pudo conectar ❌\n" +
                                "${e.message}\n" +
                                "Pendientes offline: $pending\n" +
                                "Pendientes con error: $failed"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }


    private fun showProfile() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.me()
                if (res.isSuccessful && res.body() != null) {
                    val me = res.body()!!
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Mi perfil")
                        .setMessage("Email: ${me.email}\nRol: ${me.role}\nID: ${me.id}")
                        .setPositiveButton("OK", null)
                        .show()
                } else if (res.code() == 401) {
                    Toast.makeText(this@HomeActivity, "Sesión caducada. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                    session.clearToken()
                    goToLogin()
                } else {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Mi perfil")
                        .setMessage("Error ${res.code()} ❌")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("Mi perfil")
                    .setMessage("Error de conexión ❌\n${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    
    private suspend fun ensureValidSession() {
        try {
            val res = NetworkModule.api.me()
            if (res.code() == 401) {
                Toast.makeText(
                    this@HomeActivity,
                    "Sesion caducada. Inicia sesion de nuevo.",
                    Toast.LENGTH_LONG
                ).show()
                session.clearToken()
                goToLogin()
            } else if (res.code() >= 500) {
                Toast.makeText(
                    this@HomeActivity,
                    "Error del servidor (${res.code()}).",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (_: Exception) {
            // Si no hay red, no forzamos logout
        }
    }

private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar") { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        session.clearToken()
        goToLogin()
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}

