package com.example.inventoryapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.core.view.GravityCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityHomeBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.events.EventsActivity
import com.example.inventoryapp.ui.movements.MovementsMenuActivity
import com.example.inventoryapp.ui.products.ProductListActivity
import com.example.inventoryapp.ui.scan.ScanActivity
import com.example.inventoryapp.ui.stock.StockActivity
import com.example.inventoryapp.ui.rotation.RotationActivity
import com.example.inventoryapp.ui.reports.ReportsActivity
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.categories.CategoriesActivity
import com.example.inventoryapp.ui.thresholds.ThresholdsActivity
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.SystemAlertManager
import com.example.inventoryapp.data.local.SystemAlertType


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // ✅ Si no hay token, fuera
        if (session.getToken().isNullOrBlank()) {
            goToLogin()
            return
        }

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.toolbar.setOnLongClickListener {
            showHostDialog()
            true
        }

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
            startActivity(Intent(this, MovementsMenuActivity::class.java))
        }

        binding.btnEvents.setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }

        binding.btnRotation.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val res = NetworkModule.api.me()
                    if (res.code() == 401) {
                        UiNotifier.show(this@HomeActivity, ApiErrorFormatter.format(401))
                        session.clearToken()
                        goToLogin()
                        return@launch
                    }
                    if (res.isSuccessful && res.body() != null) {
                        val role = res.body()!!.role
                        if (role == "MANAGER" || role == "ADMIN") {
                            startActivity(Intent(this@HomeActivity, RotationActivity::class.java))
                        } else {
                            UiNotifier.show(this@HomeActivity, "Permiso denegado. Permisos insuficientes.")
                        }
                    } else {
                        UiNotifier.show(this@HomeActivity, ApiErrorFormatter.format(res.code()))
                    }
                } catch (e: Exception) {
                    UiNotifier.show(this@HomeActivity, "Error de conexión: ${e.message}")
                }
            }
        }

        binding.btnReports.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }

        binding.btnCategories.setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }

        binding.btnThresholds.setOnClickListener {
            startActivity(Intent(this, ThresholdsActivity::class.java))
        }

        binding.navViewMain.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_system_status -> showSystemStatus()
                R.id.nav_alerts -> startActivity(Intent(this, AlertsActivity::class.java))
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.navViewBottom.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.nav_settings) {
                UiNotifier.show(this, "Ajustes (próximamente)")
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                return@setNavigationItemSelectedListener true
            }
            if (item.itemId == R.id.nav_logout) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                confirmLogout()
                return@setNavigationItemSelectedListener true
            }
            if (item.itemId == R.id.nav_toggle_theme) {
                toggleTheme()
                return@setNavigationItemSelectedListener true
            }
            false
        }

        updateThemeMenuItem()

        if (prefs.getBoolean("reopen_drawer", false)) {
            prefs.edit().putBoolean("reopen_drawer", false).apply()
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            ensureValidSession()
            updateDrawerHeader()

            val report = OfflineSyncer.flush(this@HomeActivity)

            if (report.sent > 0) {
                UiNotifier.showBlocking(
                    this@HomeActivity,
                    "Pendientes procesados",
                    "Se han enviado correctamente ${report.sent} pendientes offline.",
                    com.example.inventoryapp.R.drawable.ic_check_green
                )
                SystemAlertManager.record(
                    this@HomeActivity,
                    SystemAlertType.OFFLINE_SYNC_OK,
                    "Sincronización completada",
                    "Se enviaron correctamente ${report.sent} pendientes offline.",
                    blocking = false
                )
            }

            if (report.movedToFailed > 0) {
                UiNotifier.showBlocking(
                    this@HomeActivity,
                    "Pendientes con error",
                    "${report.movedToFailed} pendientes han fallado. Revisa la pestaña de Pendientes offline.",
                    com.example.inventoryapp.R.drawable.ic_error_red
                )
            }
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

    private fun showHostDialog() {
        val input = EditText(this).apply {
            hint = "IP del servidor (ej. 192.168.1.50)"
            setText(NetworkModule.getCustomHost() ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle("Configurar servidor")
            .setMessage("Se guardará solo en este dispositivo.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Limpiar") { _, _ ->
                NetworkModule.setCustomHost(null)
                UiNotifier.show(this, "Servidor restablecido")
            }
            .setPositiveButton("Guardar") { _, _ ->
                NetworkModule.setCustomHost(input.text.toString())
                UiNotifier.show(this, "Servidor actualizado")
            }
            .show()
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
                    UiNotifier.show(this@HomeActivity, ApiErrorFormatter.format(401))
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
                UiNotifier.show(this@HomeActivity, ApiErrorFormatter.format(401))
                session.clearToken()
                goToLogin()
            } else if (res.code() >= 500) {
                UiNotifier.show(this@HomeActivity, ApiErrorFormatter.format(res.code()))
            }
        } catch (_: Exception) {
            // Si no hay red, no forzamos logout
        }
    }

    private suspend fun updateDrawerHeader() {
        try {
            val header = binding.navViewMain.getHeaderView(0)
            val tvName = header.findViewById<android.widget.TextView>(com.example.inventoryapp.R.id.tvUserName)
            val tvEmail = header.findViewById<android.widget.TextView>(com.example.inventoryapp.R.id.tvUserEmail)
            val tvRole = header.findViewById<android.widget.TextView>(com.example.inventoryapp.R.id.tvUserRole)
            val res = NetworkModule.api.me()
            if (res.isSuccessful && res.body() != null) {
                val me = res.body()!!
                tvName.text = me.username
                tvEmail.text = me.email
                tvRole.text = me.role
            }
        } catch (_: Exception) {
            // Silent if offline.
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

    private fun toggleTheme() {
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        val newMode = !isDark
        prefs.edit().putBoolean("dark_mode", newMode).putBoolean("reopen_drawer", true).apply()
        val mode = if (newMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        recreate()
    }

    private fun updateThemeMenuItem() {
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        val item = binding.navViewBottom.menu.findItem(R.id.nav_toggle_theme)
        if (isDark) {
            item.title = "Tema claro"
            item.setIcon(R.drawable.ic_sun)
        } else {
            item.title = "Tema oscuro"
            item.setIcon(R.drawable.ic_moon)
        }
    }
}

