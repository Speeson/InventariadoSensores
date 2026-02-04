package com.example.inventoryapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
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
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.google.android.material.color.MaterialColors
import com.google.android.material.card.MaterialCardView


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager
    private var currentRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        applyTitleGradient()

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
        binding.toolbar.setNavigationIcon(R.drawable.menu)
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
            if (!canAccessRestricted()) {
                UiNotifier.show(this@HomeActivity, "Solo admin/manager")
                return@setOnClickListener
            }
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
            if (!canAccessRestricted()) {
                UiNotifier.show(this@HomeActivity, "Solo admin/manager")
                return@setOnClickListener
            }
            startActivity(Intent(this, ReportsActivity::class.java))
        }

        binding.btnCategories.setOnClickListener {
            if (!canAccessRestricted()) {
                UiNotifier.show(this@HomeActivity, "Solo admin/manager")
                return@setOnClickListener
            }
            startActivity(Intent(this, CategoriesActivity::class.java))
        }

        binding.btnThresholds.setOnClickListener {
            if (!canAccessRestricted()) {
                UiNotifier.show(this@HomeActivity, "Solo admin/manager")
                return@setOnClickListener
            }
            startActivity(Intent(this, ThresholdsActivity::class.java))
        }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
            binding.tvAlertsBadge.visibility = android.view.View.GONE
        }

        binding.navViewMain.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_system_status -> showSystemStatus()
                R.id.nav_alerts -> {
                    startActivity(Intent(this, AlertsActivity::class.java))
                    binding.tvAlertsBadge.visibility = android.view.View.GONE
                }
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
            loadAndShowStockAlertPopup()
            updateAlertsBadge()

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
            val rowToggle = header.findViewById<android.view.View>(com.example.inventoryapp.R.id.rowRestrictedToggle)
            val toggle = header.findViewById<androidx.appcompat.widget.SwitchCompat>(com.example.inventoryapp.R.id.switchShowRestricted)
            val res = NetworkModule.api.me()
            if (res.isSuccessful && res.body() != null) {
                val me = res.body()!!
                currentRole = me.role
                tvName.text = me.username
                tvEmail.text = me.email
                tvRole.text = me.role

                val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
                val showRestricted = prefs.getBoolean("show_restricted_cards", false)
                val isUser = me.role.equals("USER", ignoreCase = true)
                rowToggle.visibility = if (isUser) android.view.View.VISIBLE else android.view.View.GONE
                toggle.setOnCheckedChangeListener(null)
                toggle.isChecked = showRestricted
                toggle.setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("show_restricted_cards", isChecked).apply()
                    applyRestrictedUi(isChecked)
                }
                applyRestrictedUi(showRestricted)
            }
        } catch (_: Exception) {
            // Silent if offline.
        }
    }

    private fun canAccessRestricted(): Boolean {
        return !currentRole.equals("USER", ignoreCase = true)
    }

    private fun applyRestrictedUi(showRestricted: Boolean) {
        val isUser = currentRole.equals("USER", ignoreCase = true)
        if (!isUser) {
            setRestrictedCardState(
                binding.btnRotation,
                binding.ivRotationIcon,
                binding.tvRotationLabel,
                R.drawable.rotation,
                restricted = false
            )
            setRestrictedCardState(
                binding.btnReports,
                binding.ivReportsIcon,
                binding.tvReportsLabel,
                R.drawable.reports,
                restricted = false
            )
            setRestrictedCardState(
                binding.btnCategories,
                binding.ivCategoriesIcon,
                binding.tvCategoriesLabel,
                R.drawable.category,
                restricted = false
            )
            setRestrictedCardState(
                binding.btnThresholds,
                binding.ivThresholdsIcon,
                binding.tvThresholdsLabel,
                R.drawable.umbral,
                restricted = false
            )
            binding.btnRotation.visibility = android.view.View.VISIBLE
            binding.btnReports.visibility = android.view.View.VISIBLE
            binding.btnCategories.visibility = android.view.View.VISIBLE
            binding.btnThresholds.visibility = android.view.View.VISIBLE
            return
        }

        if (!showRestricted) {
            binding.btnRotation.visibility = android.view.View.GONE
            binding.btnReports.visibility = android.view.View.GONE
            binding.btnCategories.visibility = android.view.View.GONE
            binding.btnThresholds.visibility = android.view.View.GONE
            return
        }

        binding.btnRotation.visibility = android.view.View.VISIBLE
        binding.btnReports.visibility = android.view.View.VISIBLE
        binding.btnCategories.visibility = android.view.View.VISIBLE
        binding.btnThresholds.visibility = android.view.View.VISIBLE

        setRestrictedCardState(
            binding.btnRotation,
            binding.ivRotationIcon,
            binding.tvRotationLabel,
            R.drawable.rotation,
            restricted = true
        )
        setRestrictedCardState(
            binding.btnReports,
            binding.ivReportsIcon,
            binding.tvReportsLabel,
            R.drawable.reports,
            restricted = true
        )
        setRestrictedCardState(
            binding.btnCategories,
            binding.ivCategoriesIcon,
            binding.tvCategoriesLabel,
            R.drawable.category,
            restricted = true
        )
        setRestrictedCardState(
            binding.btnThresholds,
            binding.ivThresholdsIcon,
            binding.tvThresholdsLabel,
            R.drawable.umbral,
            restricted = true
        )
    }

    private fun setRestrictedCardState(
        card: MaterialCardView,
        icon: ImageView,
        label: TextView,
        originalIconRes: Int,
        restricted: Boolean
    ) {
        if (label.tag == null) {
            label.tag = label.text.toString()
        }

        if (restricted) {
            label.text = "Solo admin/manager"
            label.setTextColor(Color.DKGRAY)
            icon.setImageResource(R.drawable.ic_lock)
            icon.setColorFilter(Color.parseColor("#9E9E9E"))
            card.setCardBackgroundColor(Color.parseColor("#E0E0E0"))
            card.alpha = 0.75f
        } else {
            label.text = label.tag as String
            label.setTextColor(MaterialColors.getColor(label, com.google.android.material.R.attr.colorOnSurface))
            icon.setImageResource(originalIconRes)
            icon.setColorFilter(Color.parseColor("#4A7BF7"))
            card.setCardBackgroundColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface))
            card.alpha = 1.0f
        }
    }

    private suspend fun loadAndShowStockAlertPopup() {
        try {
            val res = NetworkModule.api.listAlerts(status = AlertStatusDto.PENDING, limit = 1, offset = 0)
            if (!res.isSuccessful || res.body() == null) return
            val alert = res.body()!!.items.firstOrNull() ?: return

            val prefs = getSharedPreferences("alert_popup", MODE_PRIVATE)
            val lastId = prefs.getInt("last_alert_id", -1)
            if (alert.id == lastId) return

            var productName: String? = null
            var location: String? = null
            try {
                val stockRes = NetworkModule.api.getStock(alert.stockId)
                if (stockRes.isSuccessful && stockRes.body() != null) {
                    val stock = stockRes.body()!!
                    location = stock.location
                    val productRes = NetworkModule.api.getProduct(stock.productId)
                    if (productRes.isSuccessful && productRes.body() != null) {
                        productName = productRes.body()!!.name
                    }
                }
            } catch (_: Exception) {
                // Keep fallback labels if lookup fails.
            }

            val productLabel = productName ?: "Producto ${alert.stockId}"
            val locationLabel = location ?: "N/D"
            val message = "Stock bajo: $productLabel\n" +
                "Cantidad: ${alert.quantity}\n" +
                "Umbral: ${alert.minQuantity}\n" +
                "Location: $locationLabel"

            AlertDialog.Builder(this@HomeActivity)
                .setTitle("Alerta de stock")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()

            prefs.edit().putInt("last_alert_id", alert.id).apply()
        } catch (_: Exception) {
            // Silent if offline or API error.
        }
    }

    private suspend fun updateAlertsBadge() {
        try {
            val res = NetworkModule.api.listAlerts(status = AlertStatusDto.PENDING, limit = 1, offset = 0)
            if (!res.isSuccessful || res.body() == null) {
                binding.tvAlertsBadge.visibility = android.view.View.GONE
                return
            }
            val total = res.body()!!.total
            if (total > 0) {
                val label = if (total > 99) "99+" else total.toString()
                binding.tvAlertsBadge.text = label
                binding.tvAlertsBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.tvAlertsBadge.visibility = android.view.View.GONE
            }
        } catch (_: Exception) {
            binding.tvAlertsBadge.visibility = android.view.View.GONE
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

    private fun applyTitleGradient() {
        binding.tvHomeTitle.post {
            val paint = binding.tvHomeTitle.paint
            val width = paint.measureText(binding.tvHomeTitle.text.toString())
            if (width <= 0f) return@post
            val shader = android.graphics.LinearGradient(
                0f,
                0f,
                width,
                0f,
                intArrayOf(
                    android.graphics.Color.parseColor("#12C2E9"),
                    android.graphics.Color.parseColor("#2C9CE2"),
                    android.graphics.Color.parseColor("#4A7BF7")
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            binding.tvHomeTitle.invalidate()
        }
    }
}




