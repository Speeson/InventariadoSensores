package com.example.inventoryapp.ui.common

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.ListView
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
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.launch

object LiquidTopNav {

    private const val PREFS_UI = "ui_prefs"
    private const val PREF_REOPEN_DRAWER = "reopen_global_drawer"
    private const val TAG = "LiquidTopNav"
    private const val MENU_CONTENT_GAP_DP = 6
    private const val TOP_CONTENT_CLEARANCE_DP = 56
    private const val LIQUID_CRYSTAL_BLUE = "#7FD8FF"
    private const val LIQUID_CRYSTAL_BLUE_ACTIVE = "#2CB8FF"
    private const val LIQUID_STATUS_OFFLINE = "#FF2A3D"
    private const val LIQUID_STATUS_ONLINE = "#36C96A"

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
            ensureTopBarAppearancePersistence(host)

            bindActions(activity, host, dismissOverlay, drawerState.drawerLayout)
            applyIconStyle(host)
            setTopActionSelection(host, currentTopActionFor(activity))
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
        val btnCenter = host.findViewById<ImageView>(R.id.btnTopCenterMain)
        val btnProfile = host.findViewById<ImageButton>(R.id.btnTopCenterActionOne)
        val btnCenterClose = host.findViewById<ImageButton>(R.id.btnTopCenterClose)
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
            setTopActionSelection(host, R.id.btnTopMidLeft)
            collapseCenterMenu(host, dismissOverlay, animate = true)
            showLocationSelectorDialog(activity, btnLocation)
        }
        btnLocation.setOnLongClickListener {
            showLocationInfo(activity)
            true
        }

        btnTopRight.setOnClickListener {
            setTopActionSelection(host, R.id.btnTopMidRight)
            collapseCenterMenu(host, dismissOverlay, animate = true)
            UiNotifier.show(activity, "Atajo superior derecho pendiente")
            btnTopRight.postDelayed({
                setTopActionSelection(host, currentTopActionFor(activity))
            }, 300L)
        }

        btnAlerts.setOnClickListener {
            setTopActionSelection(host, R.id.btnAlertsQuick)
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
        btnCenterClose.setOnClickListener {
            collapseCenterMenu(host, dismissOverlay, animate = true)
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
        drawerState.navView.itemIconTintList = ColorStateList.valueOf(Color.parseColor(LIQUID_CRYSTAL_BLUE))
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
            item.title = "Activar tema claro"
            item.setIcon(R.drawable.glass_sun)
        } else {
            item.title = "Activar tema oscuro"
            item.setIcon(R.drawable.glass_moon)
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
        setLiquidIcon(host.findViewById(R.id.btnMenu), R.drawable.glass_back)
        setLiquidIcon(host.findViewById(R.id.btnTopMidLeft), R.drawable.glass_location)
        setLiquidIcon(host.findViewById(R.id.btnTopMidRight), R.drawable.glass_setting)
        setLiquidIcon(host.findViewById(R.id.btnAlertsQuick), R.drawable.glass_noti)
        setLiquidIcon(host.findViewById<ImageView>(R.id.btnTopCenterMain), R.drawable.glass_add)
        setLiquidIcon(host.findViewById(R.id.btnTopCenterActionOne), R.drawable.glass_user)
        setLiquidIcon(host.findViewById(R.id.btnTopCenterClose), R.drawable.glass_x)
        setLiquidIcon(host.findViewById(R.id.btnTopCenterActionTwo), R.drawable.glass_logout)
        host.findViewById<ImageButton>(R.id.btnTopCenterActionOne)?.apply {
            imageAlpha = 255
            setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE), PorterDuff.Mode.SRC_IN)
        }
        host.findViewById<ImageButton>(R.id.btnTopCenterClose)?.apply {
            imageAlpha = 255
            setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE), PorterDuff.Mode.SRC_IN)
        }
        host.findViewById<ImageButton>(R.id.btnTopCenterActionTwo)?.apply {
            imageAlpha = 255
            setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE), PorterDuff.Mode.SRC_IN)
        }
    }

    private fun updateStatusTint(host: View) {
        applyTopBarAppearance(host)
    }

    private fun currentStatusColor(): Int {
        return if (NetworkModule.isManualOffline()) {
            Color.parseColor(LIQUID_STATUS_OFFLINE)
        } else {
            Color.parseColor(LIQUID_STATUS_ONLINE)
        }
    }

    private fun applyTopBarAppearance(host: View) {
        val bar = host.findViewById<BottomAppBar>(R.id.topLiquidBar) ?: return
        val overlay = host.findViewById<View>(R.id.topLiquidBarStrokeOverlay)
        val centerBtn = host.findViewById<ImageView>(R.id.btnTopCenterMain)
        val radius = dp(bar, 16).toFloat()
        val isExpanded = (bar.getTag(R.id.tag_liquid_bottom_bar_expanded) as? Boolean) == true
        val statusColor = currentStatusColor()
        bar.post { centerBtn?.translationY = dp(host, 11).toFloat() }

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
            stroke.setStroke((dp(bar, 1).toFloat() * 1.2f).toInt(), statusColor)
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
            material.setStroke(dp(bar, 1).toFloat() * 1.2f, statusColor)
            overlay?.visibility = View.GONE
        }
        material.alpha = 255
        bar.elevation = dp(bar, 18).toFloat()
        bar.invalidate()
    }

    private fun ensureTopBarAppearancePersistence(host: View) {
        val bar = host.findViewById<BottomAppBar>(R.id.topLiquidBar) ?: return
        val alreadyAttached =
            (bar.getTag(R.id.tag_liquid_bottom_bar_style_listener_attached) as? Boolean) == true
        if (alreadyAttached) return
        bar.setTag(R.id.tag_liquid_bottom_bar_style_listener_attached, true)

        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyTopBarAppearance(host)
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

        bar.post { applyTopBarAppearance(host) }
    }

    private fun expandCenterMenu(activity: AppCompatActivity, host: View, dismissOverlay: View) {
        collapseBottomCenterMenuOverlay(activity)
        val centerBtn = host.findViewById<ImageView>(R.id.btnTopCenterMain)
        val panel = host.findViewById<View>(R.id.topCenterExpandPanel)
        val topBar = host.findViewById<BottomAppBar>(R.id.topLiquidBar)

        topBar?.setTag(R.id.tag_liquid_bottom_bar_expanded, true)
        applyTopBarAppearance(host)
        dismissOverlay.visibility = View.VISIBLE
        dismissOverlay.isClickable = true
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.scaleX = 0.8f
        panel.translationY = -dp(host, 14).toFloat()
        centerBtn.animate().alpha(0f).setDuration(120L).withEndAction {
            centerBtn.visibility = View.INVISIBLE
            centerBtn.isClickable = false
            applyTopBarAppearance(host)
            host.post { applyTopBarAppearance(host) }
        }.start()
        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .translationY(0f)
            .setDuration(200L)
            .start()
    }

    private fun collapseCenterMenu(host: View, dismissOverlay: View, animate: Boolean) {
        val centerBtn = host.findViewById<ImageView>(R.id.btnTopCenterMain)
        val panel = host.findViewById<View>(R.id.topCenterExpandPanel)
        val topBar = host.findViewById<BottomAppBar>(R.id.topLiquidBar)

        if (panel.visibility != View.VISIBLE && dismissOverlay.visibility != View.VISIBLE) return
        val endAction = {
            panel.visibility = View.GONE
            panel.alpha = 0f
            panel.scaleX = 0.85f
            panel.translationY = -dp(host, 10).toFloat()
            dismissOverlay.visibility = View.GONE
            dismissOverlay.isClickable = false
            centerBtn.visibility = View.VISIBLE
            centerBtn.alpha = 0f
            centerBtn.isClickable = true
            centerBtn.animate().alpha(1f).setDuration(140L).start()
            host.findViewById<View>(R.id.topLiquidBarStrokeOverlay)?.visibility = View.GONE
            topBar?.setTag(R.id.tag_liquid_bottom_bar_expanded, false)
            applyTopBarAppearance(host)
            host.post { applyTopBarAppearance(host) }
        }
        if (!animate) {
            endAction.invoke()
            return
        }
        panel.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .translationY(-dp(host, 10).toFloat())
            .setDuration(150L)
            .withEndAction { endAction.invoke() }
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
                }
                val selectedId = prefs.getInt("selected_location_id", -1)
                var selectedIndex = locations.indexOfFirst { it.id == selectedId }

                val dialogView = LayoutInflater.from(activity)
                    .inflate(R.layout.dialog_liquid_location_selector, null)
                val listView = dialogView.findViewById<ListView>(R.id.lvLocations)
                val adapter = object : ArrayAdapter<String>(
                    activity,
                    android.R.layout.simple_list_item_single_choice,
                    labels.toMutableList()
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent)
                        (view.findViewById<android.widget.TextView>(android.R.id.text1))?.setTextColor(
                            androidx.core.content.ContextCompat.getColor(activity, R.color.liquid_popup_list_text)
                        )
                        return view
                    }
                }
                listView.adapter = adapter
                listView.choiceMode = ListView.CHOICE_MODE_SINGLE
                if (selectedIndex in locations.indices) {
                    listView.setItemChecked(selectedIndex, true)
                }
                listView.setOnItemClickListener { _, _, position, _ ->
                    selectedIndex = position
                }

                val dialog = AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .create()

                dialogView.findViewById<ImageButton>(R.id.btnLocationClose)?.setOnClickListener {
                    dialog.dismiss()
                }
                dialogView.findViewById<android.widget.Button>(R.id.btnLocationClear)?.setOnClickListener {
                    prefs.edit()
                        .remove("selected_location_id")
                        .remove("selected_location_code")
                        .remove("selected_location_description")
                        .apply()
                    updateLocationSelectorHint(activity, button)
                    UiNotifier.show(activity, "Ubicacion activa limpiada")
                    dialog.dismiss()
                }
                dialogView.findViewById<android.widget.Button>(R.id.btnLocationApply)?.setOnClickListener {
                    if (selectedIndex !in locations.indices) {
                        dialog.dismiss()
                        return@setOnClickListener
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

                dialog.show()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.setOnDismissListener {
                    activity.findViewById<View>(R.id.topMenuHost)?.let { hostView ->
                        setTopActionSelection(hostView, currentTopActionFor(activity))
                    }
                }
            } catch (e: Exception) {
                activity.findViewById<View>(R.id.topMenuHost)?.let { hostView ->
                    setTopActionSelection(hostView, currentTopActionFor(activity))
                }
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
            it.imageTintList = null
            it.clearColorFilter()
            it.setImageResource(R.drawable.glass_user)
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

    private fun currentTopActionFor(activity: AppCompatActivity): Int? {
        return when (activity) {
            is AlertsActivity -> R.id.btnAlertsQuick
            else -> null
        }
    }

    private fun setTopActionSelection(host: View, selectedId: Int?) {
        host.findViewById<ImageButton>(R.id.btnMenu)
            ?.let { updateTopActionButtonState(it, selectedId == R.id.btnMenu) }
        host.findViewById<ImageButton>(R.id.btnTopMidLeft)
            ?.let { updateTopActionButtonState(it, selectedId == R.id.btnTopMidLeft) }
        host.findViewById<ImageButton>(R.id.btnTopMidRight)
            ?.let { updateTopActionButtonState(it, selectedId == R.id.btnTopMidRight) }
        host.findViewById<ImageButton>(R.id.btnAlertsQuick)
            ?.let { updateTopActionButtonState(it, selectedId == R.id.btnAlertsQuick) }
    }

    private fun updateTopActionButtonState(button: ImageButton, active: Boolean) {
        val leftEdgeButton = button.id == R.id.btnMenu || button.id == R.id.btnTopMidLeft
        val selectedScale = if (leftEdgeButton) 1.04f else 1.08f
        if (active) {
            button.setBackgroundResource(R.drawable.bg_liquid_icon_selected)
            button.imageAlpha = 255
            button.scaleX = selectedScale
            button.scaleY = -selectedScale
            button.setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE), PorterDuff.Mode.SRC_IN)
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.imageAlpha = 245
            button.scaleX = 1.0f
            button.scaleY = -1.0f
            button.setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE), PorterDuff.Mode.SRC_IN)
        }
    }

    private fun setLiquidIcon(button: ImageView, resId: Int) {
        button.setImageResource(resId)
        button.imageTintList = null
        val prefs = button.context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        val tint = if (resId == R.drawable.glass_add) {
            if (prefs.getBoolean("dark_mode", false)) {
                Color.parseColor("#00B8FF")
            } else {
                Color.parseColor("#F2FAFF")
            }
        } else {
            Color.parseColor(LIQUID_CRYSTAL_BLUE)
        }
        button.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        button.imageAlpha = 245
    }
}
