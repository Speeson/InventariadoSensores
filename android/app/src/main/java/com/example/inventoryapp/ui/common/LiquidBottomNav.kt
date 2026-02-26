package com.example.inventoryapp.ui.common

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Paint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.inventoryapp.R
import com.example.inventoryapp.ui.audit.AuditActivity
import com.example.inventoryapp.ui.categories.CategoriesActivity
import com.example.inventoryapp.ui.events.EventsActivity
import com.example.inventoryapp.ui.home.HomeActivity
import com.example.inventoryapp.ui.imports.ImportsActivity
import com.example.inventoryapp.ui.movements.MovementsMenuActivity
import com.example.inventoryapp.ui.products.ProductListActivity
import com.example.inventoryapp.ui.reports.ReportsActivity
import com.example.inventoryapp.ui.rotation.RotationActivity
import com.example.inventoryapp.ui.scan.ConfirmScanActivity
import com.example.inventoryapp.ui.stock.StockActivity
import com.example.inventoryapp.ui.thresholds.ThresholdsActivity
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

object LiquidBottomNav {

    private enum class NavTab {
        HOME,
        SEARCH,
        SCAN,
        ASSISTANT,
        AUDIT,
        NONE,
    }

    private data class SearchTarget(
        val label: String,
        val action: String,
        val activityClass: Class<out Activity>? = null,
    )

    private const val ACTION_HOME = "home"
    private const val ACTION_MANUAL = "manual"
    private const val ACTION_SCAN = "scan"
    private const val CAMERA_PERMISSION_FRAGMENT_TAG = "liquid_camera_permission_fragment"
    private const val PREFS_UI = "ui_prefs"
    private const val MENU_CONTENT_GAP_DP = 6
    private const val LIQUID_CRYSTAL_BLUE = "#7FD8FF"
    private const val LIQUID_CRYSTAL_BLUE_ACTIVE = "#2CB8FF"
    private const val LIQUID_CRYSTAL_BLUE_LIGHT_BOOST = "#58C9FF"
    private var cameraPermissionDeniedCount = 0

    private val excluded = setOf(
        "com.example.inventoryapp.ui.auth.LoginActivity",
    )

    private val quickTargets = listOf(
        SearchTarget("Productos", "products", ProductListActivity::class.java),
        SearchTarget("Stock", "stock", StockActivity::class.java),
        SearchTarget("Movimientos", "movements", MovementsMenuActivity::class.java),
        SearchTarget("Eventos", "events", EventsActivity::class.java),
        SearchTarget("Categorias", "categories", CategoriesActivity::class.java),
        SearchTarget("Umbrales", "thresholds", ThresholdsActivity::class.java),
        SearchTarget("Importaciones", "imports", ImportsActivity::class.java),
        SearchTarget("Reportes", "reports", ReportsActivity::class.java),
        SearchTarget("Rotacion", "rotation", RotationActivity::class.java),
    )

    fun install(activity: AppCompatActivity) {
        if (excluded.contains(activity.javaClass.name)) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val existingNav = content.findViewById<View>(R.id.liquidBottomNavRoot)
        if (existingNav != null) {
            ensureDismissOverlay(activity, content, existingNav)
            applyBottomBarAppearance(existingNav)
            applyIconStyle(existingNav)
            highlightCurrentTab(activity, existingNav)
            collapseCenterMenu(existingNav, animate = false)
            val contentRoot = content.getChildAt(0) ?: content
            setupKeyboardAwareBehavior(activity, content, contentRoot, existingNav)
            return
        }
        content.setBackgroundResource(R.drawable.bg_home_gradient)

        val originalChild = content.getChildAt(0) ?: return
        val nav = LayoutInflater.from(activity).inflate(R.layout.nav_liquid_bottom_bar, content, false)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        ensureDismissOverlay(activity, content, nav)
        content.addView(nav, params)

        applyBottomBarAppearance(nav)
        bindActions(activity, nav)
        applyIconStyle(nav)
        highlightCurrentTab(activity, nav)
        setupCenterMenu(activity, nav)

        nav.post {
            if (activity !is HomeActivity) {
                ensureBottomSpace(originalChild, nav, includeNavHeight = true)
            }
        }
        setupKeyboardAwareBehavior(activity, content, originalChild, nav)
    }

    private fun applyBottomBarAppearance(nav: View) {
        val bar = nav.findViewById<BottomAppBar>(R.id.liquidBar) ?: return
        applyBottomBarAppearance(bar)
        ensureBottomBarAppearancePersistence(bar)
    }

    private fun applyBottomBarAppearance(bar: BottomAppBar) {
        val radius = dp(bar, 16).toFloat()
        val isExpanded = (bar.getTag(R.id.tag_liquid_bottom_bar_expanded) as? Boolean) == true
        bar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        bar.clipToOutline = true
        val material = bar.background as? MaterialShapeDrawable ?: return
        material.shapeAppearanceModel = material.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCornerSize(radius)
            .setTopRightCornerSize(radius)
            .setBottomLeftCornerSize(radius)
            .setBottomRightCornerSize(radius)
            .build()
        // Safe glass tuning for BottomAppBar: keep cradle geometry untouched.
        // Keep low-opacity body but preserve a bright, readable border.
        material.fillColor = ColorStateList.valueOf(Color.parseColor("#329AC7EA"))
        val expandedOverlay = (bar.parent as? View)?.findViewById<View>(R.id.liquidBarStrokeOverlay)
        if (isExpanded) {
            material.setPaintStyle(Paint.Style.FILL)
            material.setStroke(0f, Color.TRANSPARENT)
            expandedOverlay?.visibility = View.VISIBLE
        } else {
            material.setPaintStyle(Paint.Style.FILL_AND_STROKE)
            material.setStroke(dp(bar, 1).toFloat() * 1.2f, Color.parseColor("#FFFFFFFF"))
            expandedOverlay?.visibility = View.GONE
        }
        material.alpha = 255
        bar.elevation = dp(bar, 18).toFloat()
        bar.invalidate()
    }

    private fun ensureBottomBarAppearancePersistence(bar: BottomAppBar) {
        val alreadyAttached =
            (bar.getTag(R.id.tag_liquid_bottom_bar_style_listener_attached) as? Boolean) == true
        if (alreadyAttached) return
        bar.setTag(R.id.tag_liquid_bottom_bar_style_listener_attached, true)

        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyBottomBarAppearance(bar)
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

        bar.post { applyBottomBarAppearance(bar) }
    }

    private fun ensureDismissOverlay(activity: AppCompatActivity, content: ViewGroup, nav: View) {
        val existing = content.findViewById<View>(R.id.liquid_center_dismiss_overlay)
        if (existing != null) {
            existing.setOnClickListener {
                collapseCenterMenu(nav, true)
                restoreCurrentSelection(activity, nav)
            }
            return
        }
        val overlay = View(activity).apply {
            id = R.id.liquid_center_dismiss_overlay
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                collapseCenterMenu(nav, true)
                restoreCurrentSelection(activity, nav)
            }
        }
        content.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun bindActions(activity: AppCompatActivity, nav: View) {
        nav.findViewById<ImageButton>(R.id.btnLiquidHome).setOnClickListener {
            collapseCenterMenu(nav, true)
            setActiveTab(nav, NavTab.HOME)
            open(activity, HomeActivity::class.java)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidTheme).setOnClickListener {
            collapseCenterMenu(nav, true)
            setActiveTab(nav, NavTab.SEARCH)
            showQuickSearch(activity, nav)
        }
        nav.findViewById<View>(R.id.btnLiquidScan).setOnClickListener {
            if (isCenterMenuExpanded(nav)) {
                collapseCenterMenu(nav, true)
                return@setOnClickListener
            }
            expandCenterMenu(nav)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidSearch).setOnClickListener {
            collapseCenterMenu(nav, true)
            setActiveTab(nav, NavTab.ASSISTANT)
            showAssistantPlaceholder(activity, nav)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidProfile).setOnClickListener {
            collapseCenterMenu(nav, true)
            setActiveTab(nav, NavTab.AUDIT)
            open(activity, AuditActivity::class.java)
        }
    }

    private fun open(activity: AppCompatActivity, target: Class<out Activity>) {
        if (activity::class.java == target) return
        val intent = Intent(activity, target)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activity.startActivity(intent)
    }

    private fun ensureBottomSpace(contentRoot: View, nav: View, includeNavHeight: Boolean) {
        val target = resolvePaddingTarget(contentRoot)
        val initial = (target.getTag(R.id.tag_liquid_nav_original_padding_bottom) as? Int)
            ?: target.paddingBottom.also {
                target.setTag(R.id.tag_liquid_nav_original_padding_bottom, it)
            }

        val extra = if (includeNavHeight) nav.height + dp(nav, MENU_CONTENT_GAP_DP) else 0
        val targetBottom = initial + extra
        if (target.paddingBottom != targetBottom) {
            target.updatePadding(bottom = targetBottom)
        }
    }

    private fun resolvePaddingTarget(contentRoot: View): View {
        if (contentRoot is DrawerLayout) {
            val wrapped = contentRoot.findViewById<ViewGroup>(R.id.liquidTopDrawerContentContainer)
                ?.getChildAt(0)
            if (wrapped != null) return wrapped
            return if (contentRoot.childCount > 0) contentRoot.getChildAt(0) else contentRoot
        }
        if (contentRoot.id == R.id.liquidTopDrawerContentContainer && contentRoot is ViewGroup && contentRoot.childCount > 0) {
            return contentRoot.getChildAt(0)
        }
        return contentRoot
    }

    private fun setupKeyboardAwareBehavior(
        activity: AppCompatActivity,
        content: ViewGroup,
        contentRoot: View,
        nav: View
    ) {
        val alreadyAttached = (nav.getTag(R.id.tag_liquid_nav_attached) as? Boolean) == true
        if (alreadyAttached) {
            return
        }
        nav.setTag(R.id.tag_liquid_nav_attached, true)

        var lastImeVisible: Boolean? = null
        val applyKeyboardState: (Boolean) -> Unit = apply@{ imeVisible ->
            if (lastImeVisible == imeVisible) return@apply
            lastImeVisible = imeVisible
            val shouldShowNav = !imeVisible
            if (!shouldShowNav) {
                collapseCenterMenu(nav, animate = false)
                content.findViewById<View>(R.id.liquid_center_dismiss_overlay)?.visibility = View.GONE
            }
            nav.visibility = if (shouldShowNav) View.VISIBLE else View.GONE
            if (activity !is HomeActivity) {
                nav.post {
                    ensureBottomSpace(contentRoot, nav, includeNavHeight = shouldShowNav)
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            applyKeyboardState(imeVisible)
            insets
        }

        val rootView = content.rootView
        val visibleFrame = Rect()
        val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(visibleFrame)
            val heightDiff = rootView.height - visibleFrame.height()
            val imeVisible = heightDiff > dp(nav, 120)
            applyKeyboardState(imeVisible)
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        nav.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                if (rootView.viewTreeObserver.isAlive) {
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
                }
                nav.setTag(R.id.tag_liquid_nav_attached, false)
                nav.removeOnAttachStateChangeListener(this)
            }
        })

        ViewCompat.requestApplyInsets(content)
        nav.post {
            rootView.getWindowVisibleDisplayFrame(visibleFrame)
            val heightDiff = rootView.height - visibleFrame.height()
            val imeVisible = heightDiff > dp(nav, 120)
            applyKeyboardState(imeVisible)
        }
    }

    private fun applyIconStyle(nav: View) {
        val home = nav.findViewById<ImageButton>(R.id.btnLiquidHome)
        val theme = nav.findViewById<ImageButton>(R.id.btnLiquidTheme)
        val search = nav.findViewById<ImageButton>(R.id.btnLiquidSearch)
        val profile = nav.findViewById<ImageButton>(R.id.btnLiquidProfile)
        val center = nav.findViewById<ImageView>(R.id.btnLiquidScan)

        setLiquidIcon(home, R.drawable.glass_home)
        setLiquidIcon(theme, R.drawable.glass_search)
        setLiquidIcon(center, R.drawable.glass_add)
        setLiquidIcon(search, R.drawable.glass_cloud)
        setLiquidIcon(profile, R.drawable.glass_audit)

        applyBottomIconOffsets(nav, home, theme, search, profile)
    }

    private fun applyBottomIconOffsets(
        nav: View,
        home: ImageButton,
        theme: ImageButton,
        search: ImageButton,
        profile: ImageButton
    ) {
        // Enforce placement after layout; small XML translations are often visually negligible here.
        nav.post {
            home.translationX = -dp(nav, 14).toFloat()
            theme.translationX = -dp(nav, 20).toFloat()
            search.translationX = dp(nav, 10).toFloat()
            // glass_audit asset is visually shifted to the right; compensate on the container.
            profile.translationX = 0f
        }
    }

    private fun highlightCurrentTab(activity: AppCompatActivity, nav: View) {
        val tab = currentTab(activity)
        setActiveTab(nav, tab)
    }

    private fun setActiveTab(nav: View, tab: NavTab) {
        setSelected(nav.findViewById(R.id.btnLiquidHome), tab == NavTab.HOME)
        setSelected(nav.findViewById(R.id.btnLiquidTheme), tab == NavTab.SEARCH)
        setSelected(nav.findViewById(R.id.btnLiquidSearch), tab == NavTab.ASSISTANT)
        setSelected(nav.findViewById(R.id.btnLiquidProfile), tab == NavTab.AUDIT)
    }

    private fun clearSelection(nav: View) = setActiveTab(nav, NavTab.NONE)
    private fun restoreCurrentSelection(activity: AppCompatActivity, nav: View) {
        setActiveTab(nav, currentTab(activity))
    }

    private fun currentTab(activity: AppCompatActivity): NavTab {
        return when (activity) {
            is HomeActivity -> NavTab.HOME
            is ConfirmScanActivity -> NavTab.SCAN
            is AuditActivity -> NavTab.AUDIT
            else -> NavTab.NONE
        }
    }

    private fun setSelected(button: ImageButton, selected: Boolean) {
        val prefs = button.context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        val baseColor = if (prefs.getBoolean("dark_mode", false)) {
            Color.parseColor(LIQUID_CRYSTAL_BLUE)
        } else {
            Color.parseColor(LIQUID_CRYSTAL_BLUE_LIGHT_BOOST)
        }
        if (selected) {
            button.setBackgroundResource(R.drawable.bg_liquid_icon_selected)
            button.imageAlpha = 255
            button.scaleX = 1.08f
            button.scaleY = 1.08f
            button.setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE), PorterDuff.Mode.SRC_IN)
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.imageAlpha = 245
            button.scaleX = 1.0f
            button.scaleY = 1.0f
            button.setColorFilter(baseColor, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun showQuickSearch(activity: AppCompatActivity, nav: View) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_quick_nav_search, null)
        val input = dialogView.findViewById<EditText>(R.id.etQuickNavSearch)
        val list = dialogView.findViewById<ListView>(R.id.lvQuickNav)

        val labels = quickTargets.map { it.label }
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, labels.toMutableList()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view.findViewById<TextView>(android.R.id.text1))?.setTextColor(
                    ContextCompat.getColor(activity, R.color.liquid_popup_list_text)
                )
                return view
            }
        }
        list.adapter = adapter

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        val closeBtn = dialogView.findViewById<ImageButton>(R.id.btnQuickNavClose)
        closeBtn.setOnClickListener { dialog.dismiss() }

        list.setOnItemClickListener { _, _, position, _ ->
            val selectedLabel = adapter.getItem(position) ?: return@setOnItemClickListener
            val target = quickTargets.firstOrNull { it.label == selectedLabel } ?: return@setOnItemClickListener
            dialog.dismiss()
            dispatchTarget(activity, target)
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val needle = s?.toString()?.trim()?.lowercase().orEmpty()
                val filtered = if (needle.isBlank()) labels else labels.filter { it.lowercase().contains(needle) }
                adapter.clear()
                adapter.addAll(filtered)
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        dialog.setOnDismissListener { restoreCurrentSelection(activity, nav) }
        styleDialog(dialog)
    }

    private fun dispatchTarget(activity: AppCompatActivity, target: SearchTarget) {
        when (target.action) {
            ACTION_HOME -> open(activity, HomeActivity::class.java)
            ACTION_MANUAL -> showManualPopup(activity)
            ACTION_SCAN -> showScanPopup(activity)
            else -> target.activityClass?.let { open(activity, it) }
        }
    }

    private fun showAssistantPlaceholder(activity: AppCompatActivity, nav: View) {
        UiNotifier.show(activity, "Asistente IA pendiente de implementacion")
        restoreCurrentSelection(activity, nav)
    }

    private fun showManualPopup(activity: AppCompatActivity, onDismiss: (() -> Unit)? = null) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_manual, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        val input = view.findViewById<EditText>(R.id.etManualBarcode)
        view.findViewById<Button>(R.id.btnManualCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnManualAccept).setOnClickListener {
            val code = input.text?.toString()?.trim().orEmpty()
            if (!code.matches(Regex("^\\d{13}$"))) {
                input.error = "Introduce 13 digitos"
                return@setOnClickListener
            }
            dialog.dismiss()
            val intent = Intent(activity, ConfirmScanActivity::class.java)
                .putExtra("barcode", code)
                .putExtra("offline", false)
            activity.startActivity(intent)
        }
        dialog.setOnDismissListener { onDismiss?.invoke() }
        styleDialog(dialog)
    }

    private fun showScanPopup(activity: AppCompatActivity, onDismiss: (() -> Unit)? = null) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_scan, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        view.findViewById<Button>(R.id.btnScanPopupCancel).setOnClickListener {
            dialog.dismiss()
            showScanCancelWarning(activity)
        }
        view.findViewById<Button>(R.id.btnScanPopupOpen).setOnClickListener {
            dialog.dismiss()
            requestCameraPermission(
                activity = activity,
                onGranted = {
                    resetCameraDeniedCount()
                    showQuickScanCameraPopup(activity)
                },
                onDenied = { _ ->
                    val deniedCount = incrementCameraDeniedCount()
                    val showSettings = deniedCount >= 2
                    showScanCancelWarning(activity, showSettings)
                }
            )
        }
        dialog.setOnDismissListener { onDismiss?.invoke() }
        styleDialog(dialog)
    }

    private fun showQuickScanCameraPopup(activity: AppCompatActivity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_quick_scan_camera, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()

        val closeBtn = view.findViewById<ImageButton>(R.id.btnQuickScanClose)
        closeBtn.setOnClickListener { dialog.dismiss() }

        val previewView = view.findViewById<PreviewView>(R.id.previewQuickScan)
        var cameraProvider: ProcessCameraProvider? = null
        var hasNavigated = false

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(ContextCompat.getMainExecutor(activity)) { imageProxy ->
                    analyzeBarcode(imageProxy) { code ->
                        if (hasNavigated) return@analyzeBarcode
                        hasNavigated = true
                        dialog.dismiss()
                        val intent = Intent(activity, ConfirmScanActivity::class.java)
                            .putExtra("barcode", code)
                            .putExtra("offline", false)
                        activity.startActivity(intent)
                    }
                }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
                dialog.dismiss()
                showScanCancelWarning(activity)
            }
        }, ContextCompat.getMainExecutor(activity))

        dialog.setOnDismissListener {
            cameraProvider?.unbindAll()
        }

        styleDialog(dialog, widthPercent = 0.94f)
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeBarcode(imageProxy: ImageProxy, onFound: (String) -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        BarcodeScanning.getClient()
            .process(image)
            .addOnSuccessListener { barcodes ->
                val code = barcodes.firstOrNull()?.rawValue
                if (!code.isNullOrBlank()) onFound(code)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun hasCameraPermission(activity: AppCompatActivity): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission(
        activity: AppCompatActivity,
        onGranted: () -> Unit,
        onDenied: (permanentlyDenied: Boolean) -> Unit,
    ) {
        if (hasCameraPermission(activity)) {
            onGranted()
            return
        }
        val fm = activity.supportFragmentManager
        val existing = fm.findFragmentByTag(CAMERA_PERMISSION_FRAGMENT_TAG)
        if (existing != null) {
            fm.beginTransaction().remove(existing).commitAllowingStateLoss()
            fm.executePendingTransactions()
        }

        if (fm.isStateSaved) {
            val permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            onDenied(permanentlyDenied)
            return
        }

        val fragment = CameraPermissionRequestFragment().apply {
            setCallbacks(onGranted, onDenied)
        }
        fm.beginTransaction()
            .add(fragment, CAMERA_PERMISSION_FRAGMENT_TAG)
            .commitNow()
    }

    private fun showScanCancelWarning(activity: AppCompatActivity, permanentlyDenied: Boolean = false) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_scan_warning, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        val messageView = view.findViewById<TextView>(R.id.tvScanWarnMessage)
        val closeButton = view.findViewById<Button>(R.id.btnScanWarnClose)

        if (permanentlyDenied) {
            messageView.text = "El permiso de camara esta desactivado. Activalo en Ajustes para escanear codigos."
            closeButton.text = "Abrir ajustes"
            closeButton.setOnClickListener {
                openAppSettings(activity)
                dialog.dismiss()
            }
        } else {
            messageView.text = "Es necesario otorgar permiso de camara para escanear codigos."
            closeButton.text = "Entendido"
            closeButton.setOnClickListener { dialog.dismiss() }
        }

        styleDialog(dialog)
    }

    private fun openAppSettings(activity: AppCompatActivity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }

    private fun incrementCameraDeniedCount(): Int {
        cameraPermissionDeniedCount += 1
        return cameraPermissionDeniedCount
    }

    private fun resetCameraDeniedCount() {
        cameraPermissionDeniedCount = 0
    }

    private fun setupCenterMenu(activity: AppCompatActivity, nav: View) {
        val closeBtn = nav.findViewById<ImageButton>(R.id.btnCenterClose)
        val scanBtn = nav.findViewById<ImageButton>(R.id.btnCenterScanAction)
        val manualBtn = nav.findViewById<ImageButton>(R.id.btnCenterManualAction)

        setLiquidIcon(scanBtn, R.drawable.scaner)
        setLiquidIcon(manualBtn, R.drawable.glass_scanmanual)
        setLiquidIcon(closeBtn, R.drawable.glass_x)
        scanBtn.imageAlpha = 255
        manualBtn.imageAlpha = 255
        closeBtn.imageAlpha = 255
        scanBtn.setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE), PorterDuff.Mode.SRC_IN)
        manualBtn.setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE), PorterDuff.Mode.SRC_IN)
        closeBtn.setColorFilter(Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE), PorterDuff.Mode.SRC_IN)

        closeBtn.setOnClickListener {
            collapseCenterMenu(nav, true)
            restoreCurrentSelection(activity, nav)
        }
        scanBtn.setOnClickListener {
            collapseCenterMenu(nav, true)
            setActiveTab(nav, NavTab.SCAN)
            showScanPopup(activity) { restoreCurrentSelection(activity, nav) }
        }
        manualBtn.setOnClickListener {
            collapseCenterMenu(nav, true)
            setActiveTab(nav, NavTab.SCAN)
            showManualPopup(activity) { restoreCurrentSelection(activity, nav) }
        }
    }

    private fun isCenterMenuExpanded(nav: View): Boolean {
        return nav.findViewById<View>(R.id.centerExpandPanel).visibility == View.VISIBLE
    }

    private fun expandCenterMenu(nav: View) {
        val panel = nav.findViewById<View>(R.id.centerExpandPanel)
        val center = nav.findViewById<View>(R.id.btnLiquidScan)
        val overlay = nav.rootView.findViewById<View>(R.id.liquid_center_dismiss_overlay)
        nav.findViewById<BottomAppBar>(R.id.liquidBar)?.setTag(R.id.tag_liquid_bottom_bar_expanded, true)
        applyBottomBarAppearance(nav)
        overlay?.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.scaleX = 0.8f
        panel.translationY = dp(nav, 14).toFloat()
        center.animate().alpha(0f).setDuration(120L).withEndAction {
            center.visibility = View.INVISIBLE
            center.isClickable = false
            applyBottomBarAppearance(nav)
            nav.post { applyBottomBarAppearance(nav) }
        }.start()
        panel.animate().alpha(1f).scaleX(1f).translationY(0f).setDuration(200L).start()
    }

    private fun collapseCenterMenu(nav: View, animate: Boolean) {
        val panel = nav.findViewById<View>(R.id.centerExpandPanel)
        val center = nav.findViewById<View>(R.id.btnLiquidScan)
        val overlay = nav.rootView.findViewById<View>(R.id.liquid_center_dismiss_overlay)
        if (panel.visibility != View.VISIBLE) return
        nav.findViewById<BottomAppBar>(R.id.liquidBar)?.setTag(R.id.tag_liquid_bottom_bar_expanded, false)
        val endAction = {
            panel.visibility = View.GONE
            panel.alpha = 0f
            overlay?.visibility = View.GONE
            center.visibility = View.VISIBLE
            center.alpha = 0f
            center.isClickable = true
            center.animate().alpha(1f).setDuration(140L).start()
            applyBottomBarAppearance(nav)
            nav.post { applyBottomBarAppearance(nav) }
        }
        if (!animate) {
            endAction.invoke()
            return
        }
        panel.animate().alpha(0f).scaleX(0.85f).translationY(dp(nav, 10).toFloat()).setDuration(150L)
            .withEndAction { endAction.invoke() }
            .start()
    }

    private fun styleDialog(dialog: AlertDialog, widthPercent: Float = 0.9f) {
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (dialog.context.resources.displayMetrics.widthPixels * widthPercent).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun dp(view: View, value: Int): Int {
        return (value * view.resources.displayMetrics.density).toInt()
    }

    private fun setLiquidIcon(button: ImageView, resId: Int) {
        button.setImageResource(resId)
        button.imageTintList = null
        val prefs = button.context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        val baseColor = if (isDark) {
            Color.parseColor(LIQUID_CRYSTAL_BLUE)
        } else {
            Color.parseColor(LIQUID_CRYSTAL_BLUE_LIGHT_BOOST)
        }
        val tint = if (resId == R.drawable.glass_add) {
            if (isDark) {
                Color.parseColor("#00B8FF")
            } else {
                Color.parseColor("#F2FAFF")
            }
        } else {
            baseColor
        }
        button.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        button.imageAlpha = 245
    }
}

class CameraPermissionRequestFragment : Fragment() {

    private var onGranted: (() -> Unit)? = null
    private var onDenied: ((Boolean) -> Unit)? = null
    private var requested = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onGranted?.invoke()
        } else {
            val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            onDenied?.invoke(permanentlyDenied)
        }
        removeSelf()
    }

    fun setCallbacks(onGranted: () -> Unit, onDenied: (Boolean) -> Unit) {
        this.onGranted = onGranted
        this.onDenied = onDenied
    }

    override fun onStart() {
        super.onStart()
        if (requested) return
        requested = true

        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onGranted?.invoke()
            removeSelf()
            return
        }
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun removeSelf() {
        if (isAdded) {
            parentFragmentManager.beginTransaction()
                .remove(this)
                .commitAllowingStateLoss()
        }
    }
}
