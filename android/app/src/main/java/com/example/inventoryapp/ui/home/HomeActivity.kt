package com.example.inventoryapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.core.view.GravityCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R
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
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.ui.imports.ImportsActivity
import com.example.inventoryapp.ui.audit.AuditActivity
import com.google.android.material.color.MaterialColors
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager
    private var currentRole: String? = null
    private val prefs by lazy { getSharedPreferences("ui_prefs", MODE_PRIVATE) }
    private var gradientIconCache: MutableMap<Int, Bitmap> = mutableMapOf()
    private var offlineNoticeShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        session = SessionManager(this)
        NetworkModule.forceOnline()

        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // âœ… Si no hay token, fuera
        if (session.getToken().isNullOrBlank()) {
            goToLogin()
            return
        }
        if (session.isTokenExpired() && !NetworkModule.isManualOffline()) {
            session.clearToken()
            clearCachedRole()
            UiNotifier.showBlockingTimed(this@HomeActivity, "SesiÃ³n caducada. Inicia sesiÃ³n.", R.drawable.expired)
            goToLogin()
            return
        }
        syncCachedRoleWithToken()

        setSupportActionBar(binding.toolbar)
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.btnMenu.setOnLongClickListener {
            showHostDialog()
            true
        }

        // Pop-up bienvenida si venÃ­as de registro
        intent.getStringExtra("welcome_email")?.takeIf { it.isNotBlank() }?.let { email ->
            AlertDialog.Builder(this)
                .setTitle("Bienvenido")
                .setMessage("Â¡Bienvenido, $email!")
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
                showRestrictedPermissionDialog()
                return@setOnClickListener
            }
            startActivity(Intent(this@HomeActivity, RotationActivity::class.java))
        }

        binding.btnReports.setOnClickListener {
            if (!canAccessRestricted()) {
                showRestrictedPermissionDialog()
                return@setOnClickListener
            }
            startActivity(Intent(this, ReportsActivity::class.java))
        }

        binding.btnCategories.setOnClickListener {
            if (!canAccessRestricted()) {
                showRestrictedPermissionDialog()
                return@setOnClickListener
            }
            startActivity(Intent(this, CategoriesActivity::class.java))
        }

        binding.btnThresholds.setOnClickListener {
            if (!canAccessRestricted()) {
                showRestrictedPermissionDialog()
                return@setOnClickListener
            }
            startActivity(Intent(this, ThresholdsActivity::class.java))
        }
        binding.btnImports.setOnClickListener {
            if (!canAccessRestricted()) {
                showRestrictedPermissionDialog()
                return@setOnClickListener
            }
            startActivity(Intent(this, ImportsActivity::class.java))
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
                R.id.nav_audit -> {
                    if (!currentRole.equals("ADMIN", ignoreCase = true)) {
                        showRestrictedPermissionDialog()
                    } else {
                        startActivity(Intent(this, AuditActivity::class.java))
                    }
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.navViewBottom.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.nav_settings) {
                val enabled = NetworkModule.toggleManualOffline()
                updateDebugOfflineMenuItem()
                val msg = if (enabled) {
                    "Modo debug offline activado"
                } else {
                    "Modo debug offline desactivado"
                }
                UiNotifier.show(this, msg)
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
        updateDebugOfflineMenuItem()
        applyGradientIcons()
        applyCachedRoleToToggle()
        showThemeLoaderIfNeeded()

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
            updateAlertsBadge()

            NetworkModule.syncOfflineQueueWithUserNotice()
        }
        lifecycleScope.launch {
            NetworkModule.offlineState.collect { offline ->
                if (!offline) {
                    offlineNoticeShown = false
                }
            }
        }
    }
    private fun showSystemStatus() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.health()
                val checks = res.body()?.checks.orEmpty()
                val apiOk = res.isSuccessful
                val dbOk = checks["db"]?.toString().equals("ok", ignoreCase = true)
                val redisOk = checks["redis"]?.toString().equals("ok", ignoreCase = true)
                val celeryOk = checks["celery"]?.toString().equals("ok", ignoreCase = true)

                val view = layoutInflater.inflate(R.layout.dialog_system_status, null)
                setSystemStatusIcon(view, R.id.ivApiStatus, apiOk)
                setSystemStatusIcon(view, R.id.ivDbStatus, dbOk)
                setSystemStatusIcon(view, R.id.ivRedisStatus, redisOk)
                setSystemStatusIcon(view, R.id.ivCeleryStatus, celeryOk)
                showSystemStatusDialog(view)
            } catch (_: Exception) {
                val view = layoutInflater.inflate(R.layout.dialog_system_status, null)
                setSystemStatusIcon(view, R.id.ivApiStatus, false)
                setSystemStatusIcon(view, R.id.ivDbStatus, false)
                setSystemStatusIcon(view, R.id.ivRedisStatus, false)
                setSystemStatusIcon(view, R.id.ivCeleryStatus, false)
                showSystemStatusDialog(view)
            }
        }
    }

    private fun setSystemStatusIcon(view: android.view.View, iconId: Int, isOk: Boolean) {
        val image = view.findViewById<ImageView>(iconId)
        image.setImageResource(if (isOk) R.drawable.ic_check_green else R.drawable.ic_error_red)
    }

    private fun showSystemStatusDialog(view: android.view.View) {
        val dialog = AlertDialog.Builder(this@HomeActivity)
            .setView(view)
            .create()
        applySystemStatusComponentIconTint(view)
        view.findViewById<android.widget.Button>(R.id.btnCloseSystemStatus)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0f)
    }

    private fun applySystemStatusComponentIconTint(view: android.view.View) {
        val isDark = prefs.getBoolean("dark_mode", false)
        val tint = if (isDark) Color.WHITE else MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorOnSurface
        )
        val ids = intArrayOf(
            R.id.ivApiIcon,
            R.id.ivDbIcon,
            R.id.ivRedisIcon,
            R.id.ivCeleryIcon
        )
        ids.forEach { id ->
            view.findViewById<ImageView>(id)?.setColorFilter(tint)
        }
    }
    private fun showHostDialog() {
        val input = EditText(this).apply {
            hint = "IP del servidor (ej. 192.168.1.50)"
            setText(NetworkModule.getCustomHost() ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle("Configurar servidor")
            .setMessage("Se guardarÃ¡ solo en este dispositivo.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Limpiar") { _, _ ->
                NetworkModule.setCustomHost(null)
                CreateUiFeedback.showCreatedPopup(
                    activity = this,
                    title = "Servidor restablecido",
                    details = "Se ha restaurado la configuracion del servidor."
                )
            }
            .setPositiveButton("Guardar") { _, _ ->
                NetworkModule.setCustomHost(input.text.toString())
                CreateUiFeedback.showCreatedPopup(
                    activity = this,
                    title = "Servidor actualizado",
                    details = "Nueva configuracion de servidor guardada."
                )
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
                } else if (res.code() != 401) {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Mi perfil")
                        .setMessage("Error ${res.code()} âŒ")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("Mi perfil")
                    .setMessage(UiNotifier.buildConnectionMessage(this@HomeActivity, e.message))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    
    private suspend fun ensureValidSession() {
        try {
            val res = NetworkModule.api.me()
            if (res.code() >= 500) {
                UiNotifier.show(this@HomeActivity, ApiErrorFormatter.format(res.code()))
            }
        } catch (_: Exception) {
            // Global offline notice handled by NetworkModule
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
                prefs.edit()
                    .putString("cached_role", me.role)
                    .putInt("cached_user_id", me.id)
                    .putString("cached_token", session.getToken())
                    .apply()
                tvName.text = me.username
                tvEmail.text = me.email
                tvRole.text = me.role

                val showRestricted = prefs.getBoolean("show_restricted_cards", false)
                val isUser = me.role.equals("USER", ignoreCase = true)
                rowToggle.visibility = if (isUser) android.view.View.VISIBLE else android.view.View.GONE
                binding.navViewMain.menu.findItem(R.id.nav_audit)?.isVisible = me.role.equals("ADMIN", ignoreCase = true)
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
            setRestrictedCardState(
                binding.btnImports,
                binding.ivImportsIcon,
                binding.tvImportsLabel,
                R.drawable.addfile,
                restricted = false
            )
            binding.btnRotation.visibility = android.view.View.VISIBLE
            binding.btnReports.visibility = android.view.View.VISIBLE
            binding.btnCategories.visibility = android.view.View.VISIBLE
            binding.btnThresholds.visibility = android.view.View.VISIBLE
            binding.btnImports.visibility = android.view.View.VISIBLE
            return
        }

        if (!showRestricted) {
            binding.btnRotation.visibility = android.view.View.GONE
            binding.btnReports.visibility = android.view.View.GONE
            binding.btnCategories.visibility = android.view.View.GONE
            binding.btnThresholds.visibility = android.view.View.GONE
            binding.btnImports.visibility = android.view.View.GONE
            return
        }

        binding.btnRotation.visibility = android.view.View.VISIBLE
        binding.btnReports.visibility = android.view.View.VISIBLE
        binding.btnCategories.visibility = android.view.View.VISIBLE
        binding.btnThresholds.visibility = android.view.View.VISIBLE
        binding.btnImports.visibility = android.view.View.VISIBLE

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
        setRestrictedCardState(
            binding.btnImports,
            binding.ivImportsIcon,
            binding.tvImportsLabel,
            R.drawable.addfile,
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
            label.text = "Bloqueado"
            label.setTextColor(Color.DKGRAY)
            icon.setImageResource(R.drawable.ic_lock)
            icon.setColorFilter(Color.parseColor("#9E9E9E"))
            card.setCardBackgroundColor(Color.parseColor("#E0E0E0"))
            card.alpha = 0.75f
        } else {
            label.text = label.tag as String
            label.setTextColor(Color.parseColor("#EAF4FF"))
            icon.setImageBitmap(getGradientBitmap(originalIconRes))
            icon.clearColorFilter()
            card.setCardBackgroundColor(Color.parseColor("#CCFFFFFF"))
            card.alpha = 1.0f
        }
    }

    private fun showRestrictedPermissionDialog() {
        UiNotifier.showBlocking(
            this,
            "Permisos insuficientes",
            "Esta funcionalidad está disponible solo para admin/manager.",
            R.drawable.ic_lock
        )
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
        val view = layoutInflater.inflate(R.layout.dialog_logout_confirm, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        view.findViewById<android.widget.Button>(R.id.btnLogoutCancel)?.setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<android.widget.Button>(R.id.btnLogoutConfirm)?.setOnClickListener {
            dialog.dismiss()
            logout()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun logout() {
        NetworkModule.setManualOffline(false)
        session.clearToken()
        clearCachedRole()
        goToLogin()
    }

    private fun goToLogin() {
        if (!session.isTokenExpired()) return
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }

    private fun clearCachedRole() {
        prefs.edit()
            .remove("cached_role")
            .remove("cached_user_id")
            .remove("cached_token")
            .apply()
    }

    private fun syncCachedRoleWithToken() {
        val token = session.getToken()
        val cachedToken = prefs.getString("cached_token", null)
        if (!token.isNullOrBlank() && !cachedToken.isNullOrBlank() && token != cachedToken) {
            clearCachedRole()
        }
    }

    private fun toggleTheme() {
        val isDark = prefs.getBoolean("dark_mode", false)
        val newMode = !isDark
        prefs.edit()
            .putBoolean("dark_mode", newMode)
            .putBoolean("reopen_drawer", true)
            .putBoolean("theme_loading", true)
            .apply()
        val mode = if (newMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        recreate()
    }

    private fun updateThemeMenuItem() {
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

    private fun updateDebugOfflineMenuItem() {
        val item = binding.navViewBottom.menu.findItem(R.id.nav_settings)
        val enabled = NetworkModule.isManualOffline()
        item.title = if (enabled) {
            "Simular API offline: ON"
        } else {
            "Simular API offline: OFF"
        }
    }

    private fun showThemeLoaderIfNeeded() {
        if (!prefs.getBoolean("theme_loading", false)) return
        binding.themeSpinner.visibility = android.view.View.VISIBLE
        binding.themeSpinner.postDelayed({
            binding.themeSpinner.visibility = android.view.View.GONE
            prefs.edit().putBoolean("theme_loading", false).apply()
        }, 500)
    }

    private fun applyCachedRoleToToggle() {
        val header = binding.navViewMain.getHeaderView(0)
        val rowToggle = header.findViewById<android.view.View>(com.example.inventoryapp.R.id.rowRestrictedToggle)
        val toggle = header.findViewById<androidx.appcompat.widget.SwitchCompat>(com.example.inventoryapp.R.id.switchShowRestricted)
        val cachedRole = prefs.getString("cached_role", null)
        val cachedUserId = prefs.getInt("cached_user_id", -1)
        if (cachedRole.isNullOrBlank()) return
        if (cachedUserId <= 0) return
        currentRole = cachedRole
        binding.navViewMain.menu.findItem(R.id.nav_audit)?.isVisible = cachedRole.equals("ADMIN", ignoreCase = true)
        val showRestricted = prefs.getBoolean("show_restricted_cards", false)
        val isUser = cachedRole.equals("USER", ignoreCase = true)
        rowToggle.visibility = if (isUser) android.view.View.VISIBLE else android.view.View.GONE
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = showRestricted
        toggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_restricted_cards", isChecked).apply()
            applyRestrictedUi(isChecked)
        }
        applyRestrictedUi(showRestricted)
    }

    private fun applyGradientIcons() {
        setGradientImage(binding.btnAlertsQuick, R.drawable.ic_bell)
        setGradientImage(binding.btnMenu, R.drawable.menu)
        binding.root.findViewById<ImageView>(R.id.ivScanIcon)
            ?.let { setGradientImage(it, R.drawable.scaner) }
        binding.root.findViewById<ImageView>(R.id.ivEventsIcon)
            ?.let { setGradientImage(it, R.drawable.events) }
        binding.root.findViewById<ImageView>(R.id.ivProductsIcon)
            ?.let { setGradientImage(it, R.drawable.products) }
        binding.root.findViewById<ImageView>(R.id.ivStockIcon)
            ?.let { setGradientImage(it, R.drawable.stock) }
        binding.root.findViewById<ImageView>(R.id.ivMovementsIcon)
            ?.let { setGradientImage(it, R.drawable.movements) }

        // Restricted icons handled in setRestrictedCardState when needed
        setGradientImage(binding.ivCategoriesIcon, R.drawable.category)
        setGradientImage(binding.ivRotationIcon, R.drawable.rotation)
        setGradientImage(binding.ivReportsIcon, R.drawable.reports)
        setGradientImage(binding.ivThresholdsIcon, R.drawable.umbral)
        setGradientImage(binding.ivImportsIcon, R.drawable.addfile)

        applyGradientToMenu(binding.navViewMain)
        applyGradientToMenu(binding.navViewBottom)
    }

    private fun applyGradientToMenu(nav: NavigationView) {
        nav.itemIconTintList = null
        val menu = nav.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val icon = item.icon ?: continue
            val bmp = getGradientBitmapFromDrawable(icon)
            item.icon = android.graphics.drawable.BitmapDrawable(resources, bmp)
        }
    }

    private fun getGradientBitmap(resId: Int): Bitmap {
        gradientIconCache[resId]?.let { return it }
        val src = BitmapFactory.decodeResource(resources, resId)
        if (src == null) {
            val d = ContextCompat.getDrawable(this, resId)
            if (d != null) {
                return getGradientBitmapFromDrawable(d)
            }
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val colors = intArrayOf(
            ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_start),
            ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid2),
            ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid1),
            ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_end)
        )
        val shader = LinearGradient(
            0f,
            0f,
            src.width.toFloat(),
            src.height.toFloat(),
            colors,
            floatArrayOf(0f, 0.33f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        paint.xfermode = null
        gradientIconCache[resId] = out
        return out
    }

    private fun setGradientImage(view: ImageView, resId: Int) {
        val drawable = ContextCompat.getDrawable(this, resId)
        if (drawable == null) {
            view.setImageResource(resId)
            return
        }
        view.setImageBitmap(getGradientBitmapFromDrawable(drawable))
    }

    private fun getGradientBitmapFromDrawable(drawable: android.graphics.drawable.Drawable): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
        val src = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(out)
        val colors = intArrayOf(
            ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_start),
            ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid2),
            ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid1),
            ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_end)
        )
        val shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            colors,
            floatArrayOf(0f, 0.33f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        outCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        outCanvas.drawBitmap(src, 0f, 0f, paint)
        paint.xfermode = null
        return out
    }
}






