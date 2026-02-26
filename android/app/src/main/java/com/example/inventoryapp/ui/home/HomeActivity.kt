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
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import androidx.core.view.GravityCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.drawerlayout.widget.DrawerLayout
import android.content.res.ColorStateList
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
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.data.remote.model.LocationResponseDto
import com.example.inventoryapp.ui.imports.ImportsActivity
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.color.MaterialColors
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable


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
    private var topBarStrokeColor = Color.parseColor("#36C96A")
    private val liquidCrystalBlue = Color.parseColor("#7FD8FF")
    private val liquidCrystalBlueActive = Color.parseColor("#2CB8FF")
    private var selectedTopButtonId: Int? = null

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
        updateThemeMenuItem()
        updateDebugOfflineMenuItem()
        updateLocationSelectorHint()
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
        if (selectedTopButtonId == R.id.btnAlertsQuick) {
            selectedTopButtonId = null
        }
        if (selectedTopButtonId == R.id.btnMenu && !binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            selectedTopButtonId = null
        }
        applyTopButtonsSelection()
        updateThemeMenuItem()
        updateDebugOfflineMenuItem()
        updateLocationSelectorHint()
        updateTopBarConnectionTint(NetworkModule.isManualOffline())
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::binding.isInitialized &&
            ev.actionMasked == MotionEvent.ACTION_DOWN &&
            binding.drawerLayout.isDrawerOpen(GravityCompat.START)
        ) {
            val drawerBounds = Rect()
            binding.drawerContainer.getGlobalVisibleRect(drawerBounds)
            if (!drawerBounds.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupTopLiquidMenu() {
        btnTopMenu?.let { setLiquidImage(it, R.drawable.glass_menu) }
        btnTopAlerts?.let { setLiquidImage(it, R.drawable.glass_noti) }
        findViewById<ImageButton>(R.id.btnTopMidLeft)?.let {
            setLiquidImage(it, R.drawable.glass_location)
        }
        findViewById<ImageButton>(R.id.btnTopMidRight)?.let {
            setLiquidImage(it, R.drawable.glass_setting)
        }
        findViewById<ImageView>(R.id.btnTopCenterMain)?.let {
            setLiquidImage(it, R.drawable.glass_add)
            it.setOnClickListener { toggleTopCenterMenu() }
        }
        findViewById<ImageButton>(R.id.btnTopCenterActionOne)?.let { profileButton ->
            setLiquidImage(profileButton, R.drawable.glass_user)
            profileButton.imageAlpha = 255
            profileButton.setColorFilter(liquidCrystalBlueActive, PorterDuff.Mode.SRC_IN)
            profileButton.setOnClickListener {
                toggleTopCenterMenu(forceClose = true)
                showProfile()
            }
        }
        findViewById<ImageButton>(R.id.btnTopCenterActionTwo)?.let { logoutButton ->
            setLiquidImage(logoutButton, R.drawable.glass_logout)
            logoutButton.imageAlpha = 255
            logoutButton.setColorFilter(liquidCrystalBlueActive, PorterDuff.Mode.SRC_IN)
            logoutButton.setOnClickListener {
                toggleTopCenterMenu(forceClose = true)
                confirmLogout()
            }
        }
        findViewById<ImageButton>(R.id.btnTopCenterClose)?.let { closeButton ->
            setLiquidImage(closeButton, R.drawable.glass_x)
            closeButton.imageAlpha = 255
            closeButton.setColorFilter(liquidCrystalBlueActive, PorterDuff.Mode.SRC_IN)
            closeButton.setOnClickListener {
                toggleTopCenterMenu(forceClose = true)
            }
        }
        findViewById<ImageButton>(R.id.btnTopMidLeft)?.setOnClickListener {
            setTopButtonSelection(R.id.btnTopMidLeft)
            showLocationSelectorDialog()
        }
        findViewById<ImageButton>(R.id.btnTopMidLeft)?.setOnLongClickListener {
            val current = prefs.getString("selected_location_code", null)
            if (current.isNullOrBlank()) {
                UiNotifier.show(this, "Ubicacion activa: no seleccionada")
            } else {
                UiNotifier.show(this, "Ubicacion activa: $current")
            }
            true
        }
        findViewById<ImageButton>(R.id.btnTopMidRight)?.setOnClickListener {
            setTopButtonSelection(R.id.btnTopMidRight)
            UiNotifier.show(this, "Atajo superior derecho pendiente")
            findViewById<ImageButton>(R.id.btnTopMidRight)?.postDelayed({
                if (selectedTopButtonId == R.id.btnTopMidRight) {
                    setTopButtonSelection(null)
                }
            }, 300L)
        }

        btnTopMenu?.setOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                return@setOnClickListener
            }
            setTopButtonSelection(R.id.btnMenu)
            if (topMenuExpanded) toggleTopCenterMenu(forceClose = true)
            findViewById<View>(R.id.topCenterDismissOverlay)?.apply {
                visibility = View.GONE
                isClickable = false
            }
            collapseBottomCenterMenuOverlay()
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        btnTopMenu?.setOnLongClickListener {
            showHostDialog()
            true
        }
        btnTopAlerts?.setOnClickListener {
            setTopButtonSelection(R.id.btnAlertsQuick)
            startActivity(Intent(this, AlertsActivity::class.java))
            tvTopAlertsBadge?.visibility = View.GONE
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                setTopButtonSelection(R.id.btnMenu)
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
                if (selectedTopButtonId == R.id.btnMenu) {
                    setTopButtonSelection(null)
                }
            }
        })
        ensureTopBarAppearancePersistence()
        applyTopBarAppearance()
    }

    private fun setTopButtonSelection(buttonId: Int?) {
        selectedTopButtonId = buttonId
        applyTopButtonsSelection()
    }

    private fun applyTopButtonsSelection() {
        btnTopMenu?.let { updateTopButtonState(it, selectedTopButtonId == R.id.btnMenu) }
        btnTopAlerts?.let { updateTopButtonState(it, selectedTopButtonId == R.id.btnAlertsQuick) }
        findViewById<ImageButton>(R.id.btnTopMidLeft)
            ?.let { updateTopButtonState(it, selectedTopButtonId == R.id.btnTopMidLeft) }
        findViewById<ImageButton>(R.id.btnTopMidRight)
            ?.let { updateTopButtonState(it, selectedTopButtonId == R.id.btnTopMidRight) }
    }

    private fun updateTopButtonState(button: ImageButton, active: Boolean) {
        val leftEdgeButton = button.id == R.id.btnMenu || button.id == R.id.btnTopMidLeft
        val selectedScale = if (leftEdgeButton) 1.04f else 1.08f
        if (active) {
            button.setBackgroundResource(R.drawable.bg_liquid_icon_selected)
            button.imageAlpha = 255
            button.scaleX = selectedScale
            button.scaleY = -selectedScale
            button.setColorFilter(liquidCrystalBlueActive, PorterDuff.Mode.SRC_IN)
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.imageAlpha = 245
            button.scaleX = 1.0f
            button.scaleY = -1.0f
            button.setColorFilter(liquidCrystalBlue, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun updateLocationSelectorHint() {
        val currentCode = prefs.getString("selected_location_code", null)
        findViewById<ImageButton>(R.id.btnTopMidLeft)?.contentDescription = if (currentCode.isNullOrBlank()) {
            "Seleccionar ubicacion"
        } else {
            "Ubicacion activa: $currentCode"
        }
    }

    private fun showLocationSelectorDialog() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (!res.isSuccessful || res.body() == null) {
                    val msg = if (res.code() > 0) {
                        ApiErrorFormatter.format(res.code())
                    } else {
                        "No se pudo cargar ubicaciones"
                    }
                    UiNotifier.show(this@HomeActivity, msg)
                    return@launch
                }
                val locations = res.body()!!.items
                    .sortedWith(compareBy<LocationResponseDto> { it.code.lowercase() }.thenBy { it.id })
                if (locations.isEmpty()) {
                    UiNotifier.show(this@HomeActivity, "No hay ubicaciones disponibles")
                    return@launch
                }

                val labels = locations.map { location ->
                    val desc = location.description?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
                    "(${location.id}) ${location.code}$desc"
                }.toTypedArray()
                val selectedId = prefs.getInt("selected_location_id", -1)
                var selectedIndex = locations.indexOfFirst { it.id == selectedId }

                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("Seleccionar ubicacion activa")
                    .setSingleChoiceItems(labels, selectedIndex) { _, which ->
                        selectedIndex = which
                    }
                    .setNegativeButton("Cancelar", null)
                    .setNeutralButton("Limpiar") { dialog, _ ->
                        prefs.edit()
                            .remove("selected_location_id")
                            .remove("selected_location_code")
                            .remove("selected_location_description")
                            .apply()
                        updateLocationSelectorHint()
                        UiNotifier.show(this@HomeActivity, "Ubicacion activa limpiada")
                        dialog.dismiss()
                    }
                    .setPositiveButton("Aplicar") { dialog, _ ->
                        if (selectedIndex !in locations.indices) {
                            dialog.dismiss()
                            return@setPositiveButton
                        }
                        val location = locations[selectedIndex]
                        prefs.edit()
                            .putInt("selected_location_id", location.id)
                            .putString("selected_location_code", location.code)
                            .putString("selected_location_description", location.description ?: "")
                            .apply()
                        updateLocationSelectorHint()
                        UiNotifier.show(this@HomeActivity, "Ubicacion activa: ${location.code}")
                        dialog.dismiss()
                    }
                    .show().also { dlg ->
                        dlg.setOnDismissListener {
                            if (selectedTopButtonId == R.id.btnTopMidLeft) {
                                setTopButtonSelection(null)
                            }
                        }
                    }
            } catch (e: Exception) {
                if (selectedTopButtonId == R.id.btnTopMidLeft) {
                    setTopButtonSelection(null)
                }
                UiNotifier.show(
                    this@HomeActivity,
                    UiNotifier.buildConnectionMessage(this@HomeActivity, e.message)
                )
            }
        }
    }

    private fun updateThemeMenuItem() {
        val item = binding.navViewMain.menu.findItem(R.id.nav_theme) ?: return
        val isDark = prefs.getBoolean("dark_mode", false)
        if (isDark) {
            item.title = "Activar tema claro"
            item.setIcon(R.drawable.glass_sun)
        } else {
            item.title = "Activar tema oscuro"
            item.setIcon(R.drawable.glass_moon)
        }
        applyGradientToMenu(binding.navViewMain)
    }

    private fun updateTopBarConnectionTint(offline: Boolean) {
        topBarStrokeColor = if (offline) {
            Color.parseColor("#FF2A3D")
        } else {
            Color.parseColor("#36C96A")
        }
        applyTopBarAppearance()
    }

    private fun applyTopBarAppearance() {
        val bar = findViewById<BottomAppBar>(R.id.topLiquidBar) ?: return
        val overlay = findViewById<View>(R.id.topLiquidBarStrokeOverlay)
        val centerBtn = findViewById<ImageView>(R.id.btnTopCenterMain)
        val radius = dp(16f)
        val isExpanded = (bar.getTag(R.id.tag_liquid_bottom_bar_expanded) as? Boolean) == true
        bar.post { centerBtn?.translationY = dp(11f) }

        bar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        bar.clipToOutline = true
        overlay?.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        overlay?.clipToOutline = true
        (overlay?.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.let { stroke ->
            stroke.setColor(Color.TRANSPARENT)
            stroke.cornerRadius = radius
            stroke.setStroke((dp(1f) * 1.2f).toInt(), topBarStrokeColor)
        }

        val material = bar.background as? MaterialShapeDrawable ?: return
        material.shapeAppearanceModel = material.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCornerSize(radius)
            .setTopRightCornerSize(radius)
            .setBottomLeftCornerSize(radius)
            .setBottomRightCornerSize(radius)
            .build()
        material.fillColor = ColorStateList.valueOf(Color.parseColor("#329AC7EA"))
        if (isExpanded) {
            // While expanded, use overlay stroke to avoid corner clipping artifacts.
            material.setPaintStyle(Paint.Style.FILL)
            material.setStroke(0f, Color.TRANSPARENT)
            overlay?.visibility = View.VISIBLE
        } else {
            material.setPaintStyle(Paint.Style.FILL_AND_STROKE)
            material.setStroke(dp(1f) * 1.2f, topBarStrokeColor)
            overlay?.visibility = View.GONE
        }
        material.alpha = 255
        bar.elevation = dp(18f)
        bar.invalidate()
    }

    private fun ensureTopBarAppearancePersistence() {
        val bar = findViewById<BottomAppBar>(R.id.topLiquidBar) ?: return
        val alreadyAttached =
            (bar.getTag(R.id.tag_liquid_bottom_bar_style_listener_attached) as? Boolean) == true
        if (alreadyAttached) return
        bar.setTag(R.id.tag_liquid_bottom_bar_style_listener_attached, true)

        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyTopBarAppearance()
        }
        bar.addOnLayoutChangeListener(layoutListener)
        bar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                bar.removeOnLayoutChangeListener(layoutListener)
                bar.setTag(R.id.tag_liquid_bottom_bar_style_listener_attached, false)
                bar.removeOnAttachStateChangeListener(this)
            }
        })

        bar.post { applyTopBarAppearance() }
    }

    private fun handleCompactDrawerAction(itemId: Int) {
        when (itemId) {
            R.id.nav_theme -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                toggleTheme(reopenDrawer = false)
                return
            }
            R.id.nav_system_status -> showSystemStatus()
            R.id.nav_offline -> {
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
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
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
        val centerBtn = findViewById<ImageView>(R.id.btnTopCenterMain) ?: return
        val panel = findViewById<View>(R.id.topCenterExpandPanel) ?: return
        val dismissOverlay = findViewById<View>(R.id.topCenterDismissOverlay)
        val actionOne = findViewById<ImageButton>(R.id.btnTopCenterActionOne)
        val closeAction = findViewById<ImageButton>(R.id.btnTopCenterClose)
        val actionTwo = findViewById<ImageButton>(R.id.btnTopCenterActionTwo)
        val topBar = findViewById<BottomAppBar>(R.id.topLiquidBar)
        val shouldOpen = !topMenuExpanded && !forceClose
        if (topMenuExpanded == shouldOpen) return
        topMenuExpanded = shouldOpen
        topBar?.setTag(R.id.tag_liquid_bottom_bar_expanded, shouldOpen)
        applyTopBarAppearance()
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
        closeAction?.alpha = 1f
        actionTwo?.alpha = 1f
        actionOne?.scaleX = 1f
        actionOne?.scaleY = 1f
        closeAction?.scaleX = 1f
        closeAction?.scaleY = 1f
        actionTwo?.scaleX = 1f
        actionTwo?.scaleY = 1f

        if (shouldOpen) {
            collapseBottomCenterMenuOverlay()
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.scaleX = 0.8f
            panel.translationY = -dp(14f)
            centerBtn.animate().alpha(0f).setDuration(120L).withEndAction {
                centerBtn.visibility = View.INVISIBLE
                centerBtn.isClickable = false
                applyTopBarAppearance()
                panel.post { applyTopBarAppearance() }
            }.start()
            panel.animate().alpha(1f).scaleX(1f).translationY(0f).setDuration(200L).start()
            return
        }
        panel.animate().alpha(0f).scaleX(0.85f).translationY(-dp(10f)).setDuration(150L)
            .withEndAction {
                panel.visibility = View.GONE
                panel.alpha = 0f
                dismissOverlay?.visibility = View.GONE
                dismissOverlay?.isClickable = false
                centerBtn.visibility = View.VISIBLE
                centerBtn.alpha = 0f
                centerBtn.isClickable = true
                centerBtn.animate().alpha(1f).setDuration(140L).start()
                findViewById<View>(R.id.topLiquidBarStrokeOverlay)?.visibility = View.GONE
                topBar?.setTag(R.id.tag_liquid_bottom_bar_expanded, false)
                applyTopBarAppearance()
                panel.post { applyTopBarAppearance() }
            }
            .start()
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
        val view = layoutInflater.inflate(R.layout.dialog_liquid_profile, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<ImageView>(R.id.ivProfileIconPopup)?.let {
            setLiquidImage(it, R.drawable.glass_user)
        }
        view.findViewById<ImageButton>(R.id.btnProfilePopupClose)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
        val item = binding.navViewMain.menu.findItem(R.id.nav_offline) ?: return
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
        btnTopAlerts?.let { setLiquidImage(it, R.drawable.glass_noti) }
        btnTopMenu?.let { setLiquidImage(it, R.drawable.glass_menu) }
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
        nav.itemIconTintList = ColorStateList.valueOf(liquidCrystalBlue)
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

    private fun setLiquidImage(view: ImageView, resId: Int) {
        view.setImageResource(resId)
        view.imageTintList = null
        val tint = if (resId == R.drawable.glass_add) {
            if (prefs.getBoolean("dark_mode", false)) {
                Color.parseColor("#00B8FF")
            } else {
                Color.parseColor("#F2FAFF")
            }
        } else {
            liquidCrystalBlue
        }
        view.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        view.imageAlpha = 245
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
