package com.example.inventoryapp.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.LocationResponseDto
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

object LiquidTopNav {

    private const val PREFS_UI = "ui_prefs"
    private const val PREF_REOPEN_DRAWER = "reopen_global_drawer"
    private const val TAG = "LiquidTopNav"
    private const val MENU_CONTENT_GAP_DP = 6
    private const val TOP_CONTENT_CLEARANCE_DP = 56

    private data class DrawerState(
        val drawerLayout: DrawerLayout,
        val contentRoot: View,
        val navView: NavigationView,
    )

    private val excluded = setOf(
        "com.example.inventoryapp.ui.auth.LoginActivity",
        "com.example.inventoryapp.ui.home.HomeActivity",
    )

    fun install(activity: AppCompatActivity) {
        try {
            if (excluded.contains(activity.javaClass.name)) return

            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            val drawerState = ensureGlobalDrawer(activity, content) ?: return

            hideLegacyNetworkBar(drawerState.contentRoot)
            hideLegacyHeader(drawerState.contentRoot)
            ensureTopSpace(drawerState.contentRoot)

            val dismissOverlay = ensureDismissOverlay(activity, drawerState.drawerLayout)
            val host = ensureTopHost(activity, drawerState.drawerLayout)

            bindActions(activity, host, dismissOverlay, drawerState.drawerLayout)
            applyIconStyle(host)
            updateStatusTint(host)
            updateLocationSelectorHint(activity, host.findViewById(R.id.btnTopMidLeft))
            AlertsBadgeUtil.refresh(activity.lifecycleScope, host.findViewById(R.id.tvAlertsBadge))
            setupDrawerMenu(drawerState)
            collapseCenterMenu(host, dismissOverlay, animate = false)
            host.bringToFront()
        } catch (t: Throwable) {
            Log.e(TAG, "Top nav install failed in ${activity.javaClass.simpleName}", t)
        }
    }

    private fun ensureGlobalDrawer(activity: AppCompatActivity, content: ViewGroup): DrawerState? {
        val root = content.getChildAt(0) ?: return null

        if (root is DrawerLayout && root.id == R.id.liquidGlobalDrawerLayout) {
            val contentContainer = root.findViewById<ViewGroup>(R.id.liquidTopDrawerContentContainer) ?: return null
            val contentRoot = contentContainer.getChildAt(0) ?: return null
            val navView = root.findViewById<NavigationView>(R.id.liquidGlobalDrawerNavView) ?: return null
            return DrawerState(root, contentRoot, navView)
        }

        if (root is DrawerLayout) return null

        content.removeView(root)

        val drawerLayout = DrawerLayout(activity).apply {
            id = R.id.liquidGlobalDrawerLayout
            clipChildren = false
            clipToPadding = false
        }

        val contentContainer = FrameLayout(activity).apply {
            id = R.id.liquidTopDrawerContentContainer
            clipChildren = false
            clipToPadding = false
        }
        contentContainer.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        drawerLayout.addView(
            contentContainer,
            DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val sideDrawer = LayoutInflater.from(activity)
            .inflate(R.layout.nav_liquid_side_drawer, drawerLayout, false)
            .apply {
                isClickable = true
                isFocusable = true
            }
        drawerLayout.addView(
            sideDrawer,
            DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = GravityCompat.START
            }
        )

        content.addView(
            drawerLayout,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val navView = drawerLayout.findViewById<NavigationView>(R.id.liquidGlobalDrawerNavView) ?: return null
        return DrawerState(drawerLayout, root, navView)
    }

    private fun ensureDismissOverlay(activity: AppCompatActivity, drawerLayout: DrawerLayout): View {
        val existing = drawerLayout.findViewById<View>(R.id.topCenterDismissOverlay)
        if (existing != null) return existing

        val overlay = View(activity).apply {
            id = R.id.topCenterDismissOverlay
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        drawerLayout.addView(
            overlay,
            DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        return overlay
    }

    private fun ensureTopHost(activity: AppCompatActivity, drawerLayout: DrawerLayout): FrameLayout {
        val existing = drawerLayout.findViewById<FrameLayout>(R.id.topMenuHost)
        if (existing != null) return existing

        val host = FrameLayout(activity).apply {
            id = R.id.topMenuHost
            clipChildren = false
            clipToPadding = false
            elevation = dp(this, 40).toFloat()
            translationZ = dp(this, 40).toFloat()
            setPadding(dp(this, 12), 0, dp(this, 12), 0)
            LayoutInflater.from(activity).inflate(R.layout.nav_liquid_top_bar, this, true)
        }
        val params = DrawerLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(host, 96)
        ).apply {
            // DrawerLayout only accepts LEFT/RIGHT for drawer children.
            // This host is regular content, so keep NO_GRAVITY to avoid runtime crash.
            gravity = Gravity.NO_GRAVITY
            topMargin = dp(host, 12)
        }
        drawerLayout.addView(host, params)
        return host
    }

    private fun hideLegacyNetworkBar(contentRoot: View) {
        contentRoot.findViewById<View>(R.id.viewNetworkBar)?.visibility = View.GONE
    }

    private fun hideLegacyHeader(contentRoot: View) {
        val target = resolveTopPaddingTarget(contentRoot)
        val alreadyHidden = (target.getTag(R.id.tag_liquid_top_header_hidden) as? Boolean) == true
        if (alreadyHidden) return

        val back = contentRoot.findViewById<View>(R.id.btnBack)
        if (back is ImageButton) {
            (back.parent as? View)?.visibility = View.GONE
            target.setTag(R.id.tag_liquid_top_header_hidden, true)
            return
        }

        val legacyAlerts = contentRoot.findViewById<ImageButton>(R.id.btnAlertsQuick) ?: return
        val alertsParent = legacyAlerts.parent as? View
        val row = alertsParent?.parent as? View ?: return
        row.visibility = View.GONE
        target.setTag(R.id.tag_liquid_top_header_hidden, true)
    }

    private fun ensureTopSpace(contentRoot: View) {
        val target = resolveTopPaddingTarget(contentRoot)
        val initial = (target.getTag(R.id.tag_liquid_top_original_padding_top) as? Int)
            ?: target.paddingTop.also {
                target.setTag(R.id.tag_liquid_top_original_padding_top, it)
            }
        // Keep the same visual gap policy as bottom nav while reducing extra empty top space.
        val targetTop = initial + dp(contentRoot, TOP_CONTENT_CLEARANCE_DP + MENU_CONTENT_GAP_DP)
        if (target.paddingTop != targetTop) {
            target.updatePadding(top = targetTop)
        }
    }

    private fun resolveTopPaddingTarget(contentRoot: View): View {
        if (contentRoot !is ViewGroup || contentRoot.childCount == 0) return contentRoot
        for (i in 0 until contentRoot.childCount) {
            val child = contentRoot.getChildAt(i)
            if (child.id == R.id.viewNetworkBar) continue
            if (child.id == R.id.topCenterDismissOverlay) continue
            if (child.id == R.id.topMenuHost) continue
            return child
        }
        return contentRoot
    }

    private fun bindActions(
        activity: AppCompatActivity,
        host: View,
        dismissOverlay: View,
        drawerLayout: DrawerLayout,
    ) {
        val btnBack = host.findViewById<ImageButton>(R.id.btnMenu)
        val btnLocation = host.findViewById<ImageButton>(R.id.btnTopMidLeft)
        val btnTopRight = host.findViewById<ImageButton>(R.id.btnTopMidRight)
        val btnAlerts = host.findViewById<ImageButton>(R.id.btnAlertsQuick)
        val btnCenter = host.findViewById<ImageButton>(R.id.btnTopCenterMain)
        val btnProfile = host.findViewById<ImageButton>(R.id.btnTopCenterActionOne)
        val btnLogout = host.findViewById<ImageButton>(R.id.btnTopCenterActionTwo)

        btnBack.contentDescription = "Volver"
        btnBack.setOnClickListener {
            collapseCenterMenu(host, dismissOverlay, animate = true)
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }

        btnLocation.setOnClickListener {
            collapseCenterMenu(host, dismissOverlay, animate = true)
            showLocationSelectorDialog(activity, btnLocation)
        }
        btnLocation.setOnLongClickListener {
            showLocationInfo(activity)
            true
        }

        btnTopRight.setOnClickListener {
            collapseCenterMenu(host, dismissOverlay, animate = true)
            UiNotifier.show(activity, "Atajo superior derecho pendiente")
        }

        btnAlerts.setOnClickListener {
            collapseCenterMenu(host, dismissOverlay, animate = true)
            if (activity !is AlertsActivity) {
                activity.startActivity(Intent(activity, AlertsActivity::class.java))
            }
        }

        btnCenter.setOnClickListener {
            if (isCenterExpanded(host)) {
                collapseCenterMenu(host, dismissOverlay, animate = true)
            } else {
                expandCenterMenu(activity, host, dismissOverlay)
            }
        }
        btnProfile.setOnClickListener {
            collapseCenterMenu(host, dismissOverlay, animate = true)
            showProfile(activity)
        }
        btnLogout.setOnClickListener {
            collapseCenterMenu(host, dismissOverlay, animate = true)
            confirmLogout(activity)
        }

        dismissOverlay.setOnClickListener {
            collapseCenterMenu(host, dismissOverlay, animate = true)
        }
    }

    private fun setupDrawerMenu(drawerState: DrawerState) {
        drawerState.navView.setNavigationItemSelectedListener(null)
        drawerState.drawerLayout.closeDrawer(GravityCompat.START)
        drawerState.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        drawerState.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
    }

    private fun attachDrawerBehavior(
        activity: AppCompatActivity,
        drawerLayout: DrawerLayout,
        navView: NavigationView,
        host: View,
        dismissOverlay: View
    ) {
        val alreadyAttached = (drawerLayout.getTag(R.id.tag_liquid_top_drawer_listener_attached) as? Boolean) == true
        if (alreadyAttached) return

        drawerLayout.setTag(R.id.tag_liquid_top_drawer_listener_attached, true)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                collapseCenterMenu(host, dismissOverlay, animate = false)
                collapseBottomCenterMenuOverlay(activity)
                drawerView.bringToFront()
                navView.bringToFront()
                drawerLayout.invalidate()
            }
        })
    }

    private fun bindDrawerMenuItemClickFallback(
        navView: NavigationView,
        onItemSelected: (Int) -> Unit
    ) {
        val itemIds = intArrayOf(
            R.id.nav_theme,
            R.id.nav_system_status,
            R.id.nav_offline
        )
        itemIds.forEach { id ->
            navView.menu.findItem(id)?.setOnMenuItemClickListener {
                onItemSelected(id)
                true
            }
        }
    }

    private fun handleDrawerAction(
        activity: AppCompatActivity,
        drawerLayout: DrawerLayout,
        navView: NavigationView,
        host: View,
        itemId: Int,
    ) {
        var shouldCloseDrawer = true
        when (itemId) {
            R.id.nav_theme -> {
                shouldCloseDrawer = false
                toggleTheme(activity, reopenDrawer = true)
            }
            R.id.nav_system_status -> showSystemStatus(activity)
            R.id.nav_offline -> {
                val enabled = NetworkModule.toggleManualOffline()
                updateDebugOfflineMenuItem(navView)
                updateStatusTint(host)
                val msg = if (enabled) {
                    "Modo debug offline activado"
                } else {
                    "Modo debug offline desactivado"
                }
                UiNotifier.show(activity, msg)
            }
        }
        if (shouldCloseDrawer) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun updateThemeMenuItem(activity: AppCompatActivity, navView: NavigationView) {
        val item = navView.menu.findItem(R.id.nav_theme) ?: return
        val prefs = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        if (isDark) {
            item.title = "Tema: Oscuro"
            item.setIcon(R.drawable.ic_sun)
        } else {
            item.title = "Tema: Claro"
            item.setIcon(R.drawable.ic_moon)
        }
    }

    private fun updateDebugOfflineMenuItem(navView: NavigationView) {
        val item = navView.menu.findItem(R.id.nav_offline) ?: return
        val enabled = NetworkModule.isManualOffline()
        item.title = if (enabled) {
            "Simular API offline: ON"
        } else {
            "Simular API offline: OFF"
        }
    }

    private fun toggleTheme(activity: AppCompatActivity, reopenDrawer: Boolean) {
        val prefs = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        val newMode = !isDark
        prefs.edit()
            .putBoolean("dark_mode", newMode)
            .putBoolean(PREF_REOPEN_DRAWER, reopenDrawer)
            .apply()
        val mode = if (newMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        activity.recreate()
    }
    private fun showSystemStatus(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            try {
                val res = NetworkModule.api.health()
                val checks = res.body()?.checks.orEmpty()
                val apiOk = res.isSuccessful
                val dbOk = checks["db"]?.toString().equals("ok", ignoreCase = true)
                val redisOk = checks["redis"]?.toString().equals("ok", ignoreCase = true)
                val celeryOk = checks["celery"]?.toString().equals("ok", ignoreCase = true)

                val view = LayoutInflater.from(activity).inflate(R.layout.dialog_system_status, null)
                setSystemStatusIcon(view, R.id.ivApiStatus, apiOk)
                setSystemStatusIcon(view, R.id.ivDbStatus, dbOk)
                setSystemStatusIcon(view, R.id.ivRedisStatus, redisOk)
                setSystemStatusIcon(view, R.id.ivCeleryStatus, celeryOk)
                showSystemStatusDialog(activity, view)
            } catch (_: Exception) {
                val view = LayoutInflater.from(activity).inflate(R.layout.dialog_system_status, null)
                setSystemStatusIcon(view, R.id.ivApiStatus, false)
                setSystemStatusIcon(view, R.id.ivDbStatus, false)
                setSystemStatusIcon(view, R.id.ivRedisStatus, false)
                setSystemStatusIcon(view, R.id.ivCeleryStatus, false)
                showSystemStatusDialog(activity, view)
            }
        }
    }

    private fun setSystemStatusIcon(view: View, iconId: Int, isOk: Boolean) {
        val image = view.findViewById<ImageView>(iconId)
        image.setImageResource(if (isOk) R.drawable.ic_check_green else R.drawable.ic_error_red)
    }

    private fun showSystemStatusDialog(activity: AppCompatActivity, view: View) {
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()
        applySystemStatusComponentIconTint(activity, view)
        view.findViewById<android.widget.Button>(R.id.btnCloseSystemStatus)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0f)
    }

    private fun applySystemStatusComponentIconTint(activity: AppCompatActivity, view: View) {
        val prefs = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
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

    private fun applyIconStyle(host: View) {
        GradientIconUtil.applyGradient(host.findViewById<ImageButton>(R.id.btnMenu), R.drawable.back2)
        GradientIconUtil.applyGradient(host.findViewById<ImageButton>(R.id.btnTopMidLeft), R.drawable.stock)
        GradientIconUtil.applyGradient(host.findViewById<ImageButton>(R.id.btnTopMidRight), R.drawable.ajustes)
        GradientIconUtil.applyGradient(host.findViewById<ImageButton>(R.id.btnAlertsQuick), R.drawable.ic_bell)
        GradientIconUtil.applyGradient(host.findViewById<ImageButton>(R.id.btnTopCenterMain), R.drawable.plus)
        GradientIconUtil.applyGradient(host.findViewById<ImageButton>(R.id.btnTopCenterActionOne), R.drawable.user)
        GradientIconUtil.applyGradient(host.findViewById<ImageButton>(R.id.btnTopCenterActionTwo), R.drawable.logout)
    }

    private fun updateStatusTint(host: View) {
        val tintColor = if (NetworkModule.isManualOffline()) {
            Color.parseColor("#FF2A3D")
        } else {
            Color.parseColor("#36C96A")
        }
        host.findViewById<NotchedLiquidTopBarView>(R.id.topLiquidBar)?.setStatusTintColor(tintColor)
    }

    private fun expandCenterMenu(activity: AppCompatActivity, host: View, dismissOverlay: View) {
        collapseBottomCenterMenuOverlay(activity)
        val centerBtn = host.findViewById<ImageButton>(R.id.btnTopCenterMain)
        val panel = host.findViewById<View>(R.id.topCenterExpandPanel)
        val topBar = host.findViewById<NotchedLiquidTopBarView>(R.id.topLiquidBar)
        val dropDy = activity.resources.getDimension(R.dimen.top_center_drop_dy)

        dismissOverlay.visibility = View.VISIBLE
        dismissOverlay.isClickable = true
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.scaleX = 0.84f
        panel.scaleY = 0.92f
        panel.translationY = -dropDy
        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(190L)
            .start()

        GradientIconUtil.applyGradient(centerBtn, R.drawable.plus)
        centerBtn.rotation = 0f
        centerBtn.animate()
            .translationY(dropDy)
            .rotation(45f)
            .setDuration(190L)
            .withEndAction {
                centerBtn.rotation = 0f
                centerBtn.setImageResource(R.drawable.ic_close_red)
            }
            .start()
        topBar?.notchProgress = 0f
    }

    private fun collapseCenterMenu(host: View, dismissOverlay: View, animate: Boolean) {
        val centerBtn = host.findViewById<ImageButton>(R.id.btnTopCenterMain)
        val panel = host.findViewById<View>(R.id.topCenterExpandPanel)
        val topBar = host.findViewById<NotchedLiquidTopBarView>(R.id.topLiquidBar)

        if (panel.visibility != View.VISIBLE && dismissOverlay.visibility != View.VISIBLE) return

        val endAction = {
            panel.visibility = View.GONE
            panel.alpha = 0f
            panel.scaleX = 0.84f
            panel.scaleY = 0.92f
            panel.translationY = -dp(host, 12).toFloat()
            dismissOverlay.visibility = View.GONE
            dismissOverlay.isClickable = false
            centerBtn.translationY = 0f
            centerBtn.rotation = 0f
            GradientIconUtil.applyGradient(centerBtn, R.drawable.plus)
            topBar?.notchProgress = 1f
        }
        if (!animate) {
            endAction.invoke()
            return
        }
        GradientIconUtil.applyGradient(centerBtn, R.drawable.plus)
        centerBtn.rotation = 45f
        panel.animate()
            .alpha(0f)
            .scaleX(0.84f)
            .scaleY(0.92f)
            .translationY(-dp(host, 12).toFloat())
            .setDuration(170L)
            .withEndAction { endAction.invoke() }
            .start()
        centerBtn.animate()
            .translationY(0f)
            .rotation(0f)
            .setDuration(170L)
            .start()
    }

    private fun isCenterExpanded(host: View): Boolean {
        return host.findViewById<View>(R.id.topCenterExpandPanel).visibility == View.VISIBLE
    }

    private fun updateLocationSelectorHint(activity: AppCompatActivity, button: ImageButton?) {
        val prefs = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        val currentCode = prefs.getString("selected_location_code", null)
        button?.contentDescription = if (currentCode.isNullOrBlank()) {
            "Seleccionar ubicacion"
        } else {
            "Ubicacion activa: $currentCode"
        }
    }

    private fun showLocationInfo(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        val currentCode = prefs.getString("selected_location_code", null)
        if (currentCode.isNullOrBlank()) {
            UiNotifier.show(activity, "Ubicacion activa: no seleccionada")
        } else {
            UiNotifier.show(activity, "Ubicacion activa: $currentCode")
        }
    }

    private fun showLocationSelectorDialog(activity: AppCompatActivity, button: ImageButton?) {
        val prefs = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        activity.lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (!res.isSuccessful || res.body() == null) {
                    val msg = if (res.code() > 0) {
                        ApiErrorFormatter.format(res.code())
                    } else {
                        "No se pudo cargar ubicaciones"
                    }
                    UiNotifier.show(activity, msg)
                    return@launch
                }
                val locations = res.body()!!.items
                    .sortedWith(compareBy<LocationResponseDto> { it.code.lowercase() }.thenBy { it.id })
                if (locations.isEmpty()) {
                    UiNotifier.show(activity, "No hay ubicaciones disponibles")
                    return@launch
                }

                val labels = locations.map { location ->
                    val desc = location.description?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
                    "(${location.id}) ${location.code}$desc"
                }.toTypedArray()
                val selectedId = prefs.getInt("selected_location_id", -1)
                var selectedIndex = locations.indexOfFirst { it.id == selectedId }

                AlertDialog.Builder(activity)
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
                        updateLocationSelectorHint(activity, button)
                        UiNotifier.show(activity, "Ubicacion activa limpiada")
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
                        updateLocationSelectorHint(activity, button)
                        UiNotifier.show(activity, "Ubicacion activa: ${location.code}")
                        dialog.dismiss()
                    }
                    .show()
            } catch (e: Exception) {
                UiNotifier.show(
                    activity,
                    UiNotifier.buildConnectionMessage(activity, e.message)
                )
            }
        }
    }
    private fun showProfile(activity: AppCompatActivity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_profile, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        view.findViewById<ImageView>(R.id.ivProfileIconPopup)?.let {
            GradientIconUtil.applyGradient(it, R.drawable.user)
        }
        view.findViewById<ImageButton>(R.id.btnProfilePopupClose)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun confirmLogout(activity: AppCompatActivity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_logout_confirm, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        view.findViewById<android.widget.Button>(R.id.btnLogoutCancel)?.setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<android.widget.Button>(R.id.btnLogoutConfirm)?.setOnClickListener {
            dialog.dismiss()
            logout(activity)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun logout(activity: AppCompatActivity) {
        NetworkModule.setManualOffline(false)
        SessionManager(activity).clearToken()
        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }

    private fun collapseBottomCenterMenuOverlay(activity: AppCompatActivity) {
        activity.findViewById<View>(R.id.liquid_center_dismiss_overlay)?.visibility = View.GONE
        activity.findViewById<View>(R.id.centerExpandPanel)?.let { panel ->
            panel.animate().cancel()
            panel.visibility = View.GONE
            panel.alpha = 0f
            panel.scaleX = 0.8f
            panel.translationY = dp(panel, 16).toFloat()
        }
        activity.findViewById<View>(R.id.btnLiquidScan)?.let { center ->
            center.visibility = View.VISIBLE
            center.alpha = 1f
        }
    }

    private fun dp(view: View, value: Int): Int {
        return (value * view.resources.displayMetrics.density).toInt()
    }
}
