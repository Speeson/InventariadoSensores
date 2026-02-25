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
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.widget.ImageButton
import androidx.core.view.GravityCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.drawerlayout.widget.DrawerLayout
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
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.NotchedLiquidTopBarView
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.ui.imports.ImportsActivity
import com.google.android.material.color.MaterialColors
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var btnTopMenu: ImageButton? = null
    private var btnTopAlerts: ImageButton? = null
    private var tvTopAlertsBadge: TextView? = null
    private lateinit var session: SessionManager
    private var currentRole: String? = null
    private val prefs by lazy { getSharedPreferences("ui_prefs", MODE_PRIVATE) }
    private var gradientIconCache: MutableMap<Int, Bitmap> = mutableMapOf()
    private var neonIconCache: MutableMap<String, Bitmap> = mutableMapOf()
    private var offlineNoticeShown = false
    private var topMenuExpanded = false
    private var topCenterDropDy = 0f
    private var topCenterAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        btnTopMenu = findViewById(R.id.btnMenu)
        btnTopAlerts = findViewById(R.id.btnAlertsQuick)
        tvTopAlertsBadge = findViewById(R.id.tvAlertsBadge)
        findViewById<View>(R.id.viewNetworkBar)?.visibility = View.GONE

        session = SessionManager(this)
        NetworkModule.forceOnline()
        updateTopBarConnectionTint(NetworkModule.isManualOffline())

        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¦ Si no hay token, fuera
        if (session.getToken().isNullOrBlank()) {
            goToLogin()
            return
        }
        if (session.isTokenExpired() && !NetworkModule.isManualOffline()) {
            session.clearToken()
            clearCachedRole()
            UiNotifier.showBlockingTimed(this@HomeActivity, "SesiÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n caducada. Inicia sesiÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³n.", R.drawable.expired)
            goToLogin()
            return
        }
        syncCachedRoleWithToken()

        setSupportActionBar(binding.toolbar)
        setupTopLiquidMenu()
        findViewById<View>(R.id.topMenuHost)?.bringToFront()
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        binding.navViewMain.bringToFront()

        // Pop-up bienvenida si venÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­as de registro
        intent.getStringExtra("welcome_email")?.takeIf { it.isNotBlank() }?.let { email ->
            AlertDialog.Builder(this)
                .setTitle("Bienvenido")
                .setMessage("ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡Bienvenido, $email!")
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
        binding.navViewMain.setNavigationItemSelectedListener { item ->
            handleCompactDrawerAction(item.itemId)
            true
        }
        binding.navViewMain.menu.findItem(R.id.nav_system_status)?.setOnMenuItemClickListener {
            handleCompactDrawerAction(R.id.nav_system_status)
            true
        }
        binding.navViewMain.menu.findItem(R.id.nav_settings)?.setOnMenuItemClickListener {
            handleCompactDrawerAction(R.id.nav_settings)
            true
        }
        binding.navViewMain.menu.findItem(R.id.nav_logout)?.setOnMenuItemClickListener {
            handleCompactDrawerAction(R.id.nav_logout)
            true
        }
        updateDebugOfflineMenuItem()
        applyGradientIcons()
        applyCachedRoleToToggle()
        showThemeLoaderIfNeeded()

        if (prefs.getBoolean("reopen_drawer", false)) {
            prefs.edit().putBoolean("reopen_drawer", false).apply()
            binding.drawerLayout.openDrawer(binding.navViewMain)
        }

    }

    override fun onResume() {
        super.onResume()
        btnTopMenu?.let { updateTopButtonState(it, false) }
        btnTopAlerts?.let { updateTopButtonState(it, false) }
        if (topMenuExpanded) toggleTopCenterMenu(forceClose = true)
        findViewById<View>(R.id.topCenterDismissOverlay)?.visibility = View.GONE
        collapseBottomCenterMenuOverlay()

        lifecycleScope.launch {
            ensureValidSession()
            updateDrawerHeader()
            updateAlertsBadge()

            NetworkModule.syncOfflineQueueWithUserNotice()
        }
        lifecycleScope.launch {
            NetworkModule.offlineState.collect { offline ->
                updateTopBarConnectionTint(offline)
                if (!offline) {
                    offlineNoticeShown = false
                }
            }
        }
    }

    private fun setupTopLiquidMenu() {
        btnTopMenu?.let { GradientIconUtil.applyGradient(it, R.drawable.menu) }
        btnTopAlerts?.let { GradientIconUtil.applyGradient(it, R.drawable.ic_bell) }
        findViewById<ImageButton>(R.id.btnTopMidLeft)?.let {
            GradientIconUtil.applyGradient(it, R.drawable.search)
        }
        updateTopThemeButtonIcon()
        findViewById<ImageButton>(R.id.btnTopCenterMain)?.let {
            GradientIconUtil.applyGradient(it, R.drawable.plus)
            it.setOnClickListener { toggleTopCenterMenu() }
        }
        findViewById<ImageButton>(R.id.btnTopCenterActionOne)?.setOnClickListener {
            UiNotifier.show(this, "Accion superior central 1 pendiente")
            toggleTopCenterMenu(forceClose = true)
        }
        findViewById<ImageButton>(R.id.btnTopCenterActionTwo)?.setOnClickListener {
            UiNotifier.show(this, "Accion superior central 2 pendiente")
            toggleTopCenterMenu(forceClose = true)
        }
        findViewById<ImageButton>(R.id.btnTopMidLeft)?.setOnClickListener {
            UiNotifier.show(this, "Accion superior izquierda pendiente")
        }
        findViewById<ImageButton>(R.id.btnTopMidRight)?.setOnClickListener {
            toggleTheme(reopenDrawer = false)
        }

        btnTopMenu?.setOnClickListener {
            btnTopMenu?.let { button -> updateTopButtonState(button, true) }
            if (topMenuExpanded) toggleTopCenterMenu(forceClose = true)
            findViewById<View>(R.id.topCenterDismissOverlay)?.apply {
                visibility = View.GONE
                isClickable = false
            }
            collapseBottomCenterMenuOverlay()
            binding.drawerLayout.openDrawer(binding.navViewMain)
        }
        btnTopMenu?.setOnLongClickListener {
            showHostDialog()
            true
        }
        btnTopAlerts?.setOnClickListener {
            btnTopAlerts?.let { button -> updateTopButtonState(button, true) }
            startActivity(Intent(this, AlertsActivity::class.java))
            tvTopAlertsBadge?.visibility = View.GONE
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                btnTopMenu?.let { updateTopButtonState(it, true) }
                if (topMenuExpanded) toggleTopCenterMenu(forceClose = true)
                findViewById<View>(R.id.topCenterDismissOverlay)?.apply {
                    visibility = View.GONE
                    isClickable = false
                    setOnTouchListener(null)
                    setOnClickListener(null)
                }
                collapseBottomCenterMenuOverlay()
                drawerView.bringToFront()
            }

            override fun onDrawerClosed(drawerView: View) {
                btnTopMenu?.let { updateTopButtonState(it, false) }
            }
        })
    }

    private fun updateTopButtonState(button: ImageButton, active: Boolean) {
        if (active) {
            button.setBackgroundResource(R.drawable.bg_liquid_icon_selected)
            button.imageAlpha = 255
            button.scaleX = 1.04f
            button.scaleY = 1.04f
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.imageAlpha = 235
            button.scaleX = 1.0f
            button.scaleY = 1.0f
        }
    }

    private fun updateTopThemeButtonIcon() {
        val isDark = prefs.getBoolean("dark_mode", false)
        val iconRes = if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
        findViewById<ImageButton>(R.id.btnTopMidRight)?.let {
            GradientIconUtil.applyGradient(it, iconRes)
        }
    }

    private fun updateTopBarConnectionTint(offline: Boolean) {
        val tintColor = if (offline) {
            Color.parseColor("#F44336")
        } else {
            Color.parseColor("#36C96A")
        }
        findViewById<NotchedLiquidTopBarView>(R.id.topLiquidBar)?.setStatusTintColor(tintColor)
    }

    private fun handleCompactDrawerAction(itemId: Int) {
        when (itemId) {
            R.id.nav_system_status -> showSystemStatus()
            R.id.nav_settings -> {
                val enabled = NetworkModule.toggleManualOffline()
                updateDebugOfflineMenuItem()
                updateTopBarConnectionTint(enabled)
                val msg = if (enabled) {
                    "Modo debug offline activado"
                } else {
                    "Modo debug offline desactivado"
                }
                UiNotifier.show(this, msg)
            }
            R.id.nav_logout -> confirmLogout()
        }
        binding.drawerLayout.closeDrawer(binding.navViewMain)
    }

    private fun collapseBottomCenterMenuOverlay() {
        findViewById<View>(R.id.liquid_center_dismiss_overlay)?.visibility = View.GONE
        findViewById<View>(R.id.centerExpandPanel)?.let { panel ->
            panel.animate().cancel()
            panel.visibility = View.GONE
            panel.alpha = 0f
            panel.scaleX = 0.8f
            panel.translationY = dp(16f)
        }
        findViewById<View>(R.id.btnLiquidScan)?.let { center ->
            center.visibility = View.VISIBLE
            center.alpha = 1f
        }
    }

    private fun toggleTopCenterMenu(forceClose: Boolean = false) {
        val centerBtn = findViewById<ImageButton>(R.id.btnTopCenterMain) ?: return
        val panel = findViewById<View>(R.id.topCenterExpandPanel) ?: return
        val dismissOverlay = findViewById<View>(R.id.topCenterDismissOverlay)
        val actionOne = findViewById<ImageButton>(R.id.btnTopCenterActionOne)
        val actionTwo = findViewById<ImageButton>(R.id.btnTopCenterActionTwo)
        val topBar = findViewById<NotchedLiquidTopBarView>(R.id.topLiquidBar)
        val shouldOpen = !topMenuExpanded && !forceClose
        if (topMenuExpanded == shouldOpen) return
        topMenuExpanded = shouldOpen
        topCenterDropDy = resources.getDimension(R.dimen.top_center_drop_dy)
        topCenterAnimator?.cancel()
        dismissOverlay?.bringToFront()
        findViewById<View>(R.id.topMenuHost)?.bringToFront()
        panel.bringToFront()
        centerBtn.bringToFront()
        dismissOverlay?.setOnClickListener {
            toggleTopCenterMenu(forceClose = true)
        }
        dismissOverlay?.setOnTouchListener { _, _ ->
            toggleTopCenterMenu(forceClose = true)
            true
        }
        dismissOverlay?.visibility = if (shouldOpen) View.VISIBLE else View.GONE
        dismissOverlay?.isClickable = shouldOpen

        panel.visibility = View.VISIBLE
        centerBtn.visibility = View.VISIBLE
        actionOne?.alpha = 1f
        actionTwo?.alpha = 1f
        actionOne?.scaleX = 1f
        actionOne?.scaleY = 1f
        actionTwo?.scaleX = 1f
        actionTwo?.scaleY = 1f

        if (!shouldOpen) {
            GradientIconUtil.applyGradient(centerBtn, R.drawable.plus)
            centerBtn.rotation = 45f
        } else {
            GradientIconUtil.applyGradient(centerBtn, R.drawable.plus)
            centerBtn.rotation = 0f
        }

        val from = if (shouldOpen) 0f else 1f
        val to = if (shouldOpen) 1f else 0f
        topCenterAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = if (shouldOpen) 260L else 220L
            addUpdateListener { anim ->
                val t = easeInOut(anim.animatedValue as Float)
                centerBtn.translationY = topCenterDropDy * t
                centerBtn.rotation = 45f * t
                panel.translationY = -topCenterDropDy * (1f - t)
                panel.scaleX = t
                panel.scaleY = 0.95f + (1f - 0.95f) * t
                topBar?.notchProgress = 1f - t
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!topMenuExpanded) {
                        panel.visibility = View.GONE
                        panel.scaleX = 0f
                        panel.scaleY = 0.95f
                        panel.translationY = -topCenterDropDy
                        centerBtn.translationY = 0f
                        centerBtn.rotation = 0f
                        GradientIconUtil.applyGradient(centerBtn, R.drawable.plus)
                        dismissOverlay?.visibility = View.GONE
                        dismissOverlay?.isClickable = false
                    } else {
                        centerBtn.translationY = topCenterDropDy
                        centerBtn.rotation = 0f
                        centerBtn.setImageResource(R.drawable.ic_close_red)
                    }
                }
            })
            start()
        }
    }

    private fun easeInOut(t: Float): Float {
        return (t * t * (3f - 2f * t)).coerceIn(0f, 1f)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
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
            .setMessage("Se guardarÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ solo en este dispositivo.")
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
                        .setMessage("Error ${res.code()} ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢")
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
            val res = NetworkModule.api.me()
            if (res.isSuccessful && res.body() != null) {
                val me = res.body()!!
                currentRole = me.role
                prefs.edit()
                    .putString("cached_role", me.role)
                    .putInt("cached_user_id", me.id)
                    .putString("cached_token", session.getToken())
                    .apply()
                val showRestricted = prefs.getBoolean("show_restricted_cards", false)
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
            card.setCardBackgroundColor(Color.parseColor("#00000000"))
            card.alpha = 1.0f
        } else {
            label.text = label.tag as String
            label.setTextColor(Color.parseColor("#111111"))
            setNeonImage(icon, originalIconRes)
            icon.clearColorFilter()
            card.setCardBackgroundColor(Color.parseColor("#00000000"))
            card.alpha = 1.0f
        }
    }

    private fun showRestrictedPermissionDialog() {
        UiNotifier.showBlocking(
            this,
            "Permisos insuficientes",
            "Esta funcionalidad estÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ disponible solo para admin/manager.",
            R.drawable.ic_lock
        )
    }


    private suspend fun updateAlertsBadge() {
        try {
            val res = NetworkModule.api.listAlerts(status = AlertStatusDto.PENDING, limit = 1, offset = 0)
            if (!res.isSuccessful || res.body() == null) {
                tvTopAlertsBadge?.visibility = android.view.View.GONE
                return
            }
            val total = res.body()!!.total
            if (total > 0) {
                val label = if (total > 99) "99+" else total.toString()
                tvTopAlertsBadge?.text = label
                tvTopAlertsBadge?.visibility = android.view.View.VISIBLE
            } else {
                tvTopAlertsBadge?.visibility = android.view.View.GONE
            }
        } catch (_: Exception) {
            tvTopAlertsBadge?.visibility = android.view.View.GONE
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

    private fun toggleTheme(reopenDrawer: Boolean = false) {
        val isDark = prefs.getBoolean("dark_mode", false)
        val newMode = !isDark
        prefs.edit()
            .putBoolean("dark_mode", newMode)
            .putBoolean("reopen_drawer", reopenDrawer)
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

    private fun updateDebugOfflineMenuItem() {
        val item = binding.navViewMain.menu.findItem(R.id.nav_settings)
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
        val cachedRole = prefs.getString("cached_role", null)
        val cachedUserId = prefs.getInt("cached_user_id", -1)
        if (cachedRole.isNullOrBlank()) return
        if (cachedUserId <= 0) return
        currentRole = cachedRole
        val showRestricted = prefs.getBoolean("show_restricted_cards", false)
        applyRestrictedUi(showRestricted)
    }

    private fun applyGradientIcons() {
        btnTopAlerts?.let { setGradientImage(it, R.drawable.ic_bell) }
        btnTopMenu?.let { setGradientImage(it, R.drawable.menu) }
        binding.root.findViewById<ImageView>(R.id.ivScanIcon)
            ?.let { setNeonImage(it, R.drawable.scaner) }
        binding.root.findViewById<ImageView>(R.id.ivEventsIcon)
            ?.let { setNeonImage(it, R.drawable.events) }
        binding.root.findViewById<ImageView>(R.id.ivProductsIcon)
            ?.let { setNeonImage(it, R.drawable.products) }
        binding.root.findViewById<ImageView>(R.id.ivStockIcon)
            ?.let { setNeonImage(it, R.drawable.stock) }
        binding.root.findViewById<ImageView>(R.id.ivMovementsIcon)
            ?.let { setNeonImage(it, R.drawable.movements) }

        // Restricted icons handled in setRestrictedCardState when needed
        setNeonImage(binding.ivCategoriesIcon, R.drawable.category)
        setNeonImage(binding.ivRotationIcon, R.drawable.rotation)
        setNeonImage(binding.ivReportsIcon, R.drawable.reports)
        setNeonImage(binding.ivThresholdsIcon, R.drawable.umbral)
        setNeonImage(binding.ivImportsIcon, R.drawable.addfile)

        applyGradientToMenu(binding.navViewMain)
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

    private fun setNeonImage(view: ImageView, resId: Int) {
        view.setImageBitmap(getNeonBitmap(resId))
    }

    private fun getNeonBitmap(resId: Int): Bitmap {
        val (c1, c2) = neonColorsFor(resId)
        val key = "$resId:$c1:$c2"
        neonIconCache[key]?.let { return it }

        val src = BitmapFactory.decodeResource(resources, resId)
        if (src == null) {
            val d = ContextCompat.getDrawable(this, resId)
            if (d != null) return getGradientBitmapFromDrawable(d)
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val shader = LinearGradient(
            0f,
            0f,
            src.width.toFloat(),
            src.height.toFloat(),
            intArrayOf(c1, c2),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        val glowBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            alpha = 235
        }
        // First glow pass.
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), glowBase)
        // Second glow pass to intensify neon effect.
        val glowBoost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            alpha = 210
        }
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), glowBoost)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        paint.xfermode = null

        neonIconCache[key] = out
        return out
    }

    private fun neonColorsFor(resId: Int): Pair<Int, Int> {
        return when (resId) {
            R.drawable.scaner -> Pair(Color.parseColor("#00FF2A"), Color.parseColor("#CCFF00"))
            R.drawable.products -> Pair(Color.parseColor("#FF0030"), Color.parseColor("#FF3D6E"))
            R.drawable.events -> Pair(Color.parseColor("#FFE100"), Color.parseColor("#FFF56A"))
            R.drawable.stock -> Pair(Color.parseColor("#00FFA0"), Color.parseColor("#44FFD1"))
            R.drawable.movements -> Pair(Color.parseColor("#00F5FF"), Color.parseColor("#6EFCFF"))
            R.drawable.category -> Pair(Color.parseColor("#F000FF"), Color.parseColor("#FF77FF"))
            R.drawable.rotation -> Pair(Color.parseColor("#FF7A00"), Color.parseColor("#FFC547"))
            R.drawable.reports -> Pair(Color.parseColor("#0088FF"), Color.parseColor("#4AB6FF"))
            R.drawable.umbral -> Pair(Color.parseColor("#FF006B"), Color.parseColor("#FF4D94"))
            R.drawable.addfile -> Pair(Color.parseColor("#6B00FF"), Color.parseColor("#B067FF"))
            else -> Pair(
                ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_start),
                ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_end)
            )
        }
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
