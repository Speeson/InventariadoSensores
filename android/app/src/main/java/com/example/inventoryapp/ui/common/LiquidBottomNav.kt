package com.example.inventoryapp.ui.common

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

object LiquidBottomNav {

    private enum class NavTab {
        HOME,
        SEARCH,
        SCAN,
        AUDIT,
        PROFILE,
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
    private const val ACTION_PROFILE = "profile"
    private const val CAMERA_PERMISSION_FRAGMENT_TAG = "liquid_camera_permission_fragment"
    private var cameraPermissionDeniedCount = 0

    private val excluded = setOf(
        "com.example.inventoryapp.ui.auth.LoginActivity",
        "com.example.inventoryapp.ui.alerts.AlertsActivity",
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
            applyIconStyle(existingNav)
            highlightCurrentTab(activity, existingNav)
            collapseCenterMenu(existingNav, animate = false)
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

        bindActions(activity, nav)
        applyIconStyle(nav)
        highlightCurrentTab(activity, nav)
        setupCenterMenu(activity, nav)

        nav.post {
            ensureBottomSpace(originalChild, nav)
        }
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
        nav.findViewById<ImageButton>(R.id.btnLiquidScan).setOnClickListener {
            if (isCenterMenuExpanded(nav)) {
                collapseCenterMenu(nav, true)
                return@setOnClickListener
            }
            setActiveTab(nav, NavTab.SCAN)
            expandCenterMenu(nav)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidSearch).setOnClickListener {
            collapseCenterMenu(nav, true)
            setActiveTab(nav, NavTab.AUDIT)
            open(activity, AuditActivity::class.java)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidProfile).setOnClickListener {
            collapseCenterMenu(nav, true)
            setActiveTab(nav, NavTab.PROFILE)
            showProfilePopup(activity, nav)
        }
    }

    private fun open(activity: AppCompatActivity, target: Class<out Activity>) {
        if (activity::class.java == target) return
        val intent = Intent(activity, target)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activity.startActivity(intent)
    }

    private fun ensureBottomSpace(contentRoot: View, nav: View) {
        val target = if (contentRoot is DrawerLayout && contentRoot.childCount > 0) {
            contentRoot.getChildAt(0)
        } else {
            contentRoot
        }

        val initial = (target.getTag(R.id.tag_liquid_nav_original_padding_bottom) as? Int)
            ?: target.paddingBottom.also {
                target.setTag(R.id.tag_liquid_nav_original_padding_bottom, it)
            }

        val extra = nav.height + dp(nav, 8)
        val targetBottom = initial + extra
        if (target.paddingBottom < targetBottom) {
            target.updatePadding(bottom = targetBottom)
        }
    }

    private fun applyIconStyle(nav: View) {
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidHome), R.drawable.home)
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidTheme), R.drawable.search)
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidScan), R.drawable.plus)
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidSearch), R.drawable.reports)
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidProfile), R.drawable.user)
    }

    private fun highlightCurrentTab(activity: AppCompatActivity, nav: View) {
        val tab = currentTab(activity)
        setActiveTab(nav, tab)
    }

    private fun setActiveTab(nav: View, tab: NavTab) {
        setSelected(nav.findViewById(R.id.btnLiquidHome), tab == NavTab.HOME)
        setSelected(nav.findViewById(R.id.btnLiquidTheme), tab == NavTab.SEARCH)
        setSelected(nav.findViewById(R.id.btnLiquidScan), tab == NavTab.SCAN)
        setSelected(nav.findViewById(R.id.btnLiquidSearch), tab == NavTab.AUDIT)
        setSelected(nav.findViewById(R.id.btnLiquidProfile), tab == NavTab.PROFILE)
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
        val isCenter = button.id == R.id.btnLiquidScan
        if (isCenter) {
            button.setBackgroundResource(R.drawable.bg_liquid_center_square)
            button.imageAlpha = if (selected) 255 else 235
            button.scaleX = if (selected) 1.06f else 1.0f
            button.scaleY = if (selected) 1.06f else 1.0f
            return
        }
        if (selected) {
            button.setBackgroundResource(R.drawable.bg_liquid_icon_selected)
            button.imageAlpha = 255
            button.scaleX = 1.04f
            button.scaleY = 1.04f
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.imageAlpha = 230
            button.scaleX = 1.0f
            button.scaleY = 1.0f
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
        val nav = activity.findViewById<View>(R.id.liquidBottomNavRoot)
        when (target.action) {
            ACTION_HOME -> open(activity, HomeActivity::class.java)
            ACTION_MANUAL -> showManualPopup(activity)
            ACTION_SCAN -> showScanPopup(activity)
            ACTION_PROFILE -> if (nav != null) showProfilePopup(activity, nav)
            else -> target.activityClass?.let { open(activity, it) }
        }
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

    private fun showProfilePopup(activity: AppCompatActivity, nav: View) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_profile, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        view.findViewById<ImageView>(R.id.ivProfileIconPopup)?.let {
            GradientIconUtil.applyGradient(it, R.drawable.user)
        }
        val closeBtn = view.findViewById<ImageButton>(R.id.btnProfilePopupClose)
        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { restoreCurrentSelection(activity, nav) }
        styleDialog(dialog, widthPercent = 0.9f)
    }

    private fun setupCenterMenu(activity: AppCompatActivity, nav: View) {
        val closeBtn = nav.findViewById<ImageButton>(R.id.btnCenterClose)
        val scanBtn = nav.findViewById<ImageButton>(R.id.btnCenterScanAction)
        val manualBtn = nav.findViewById<ImageButton>(R.id.btnCenterManualAction)

        GradientIconUtil.applyGradient(scanBtn, R.drawable.scaner)
        GradientIconUtil.applyGradient(manualBtn, R.drawable.code_manual)

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
        overlay?.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.scaleX = 0.8f
        panel.translationY = dp(nav, 14).toFloat()
        center.animate().alpha(0f).setDuration(120L).withEndAction {
            center.visibility = View.INVISIBLE
        }.start()
        panel.animate().alpha(1f).scaleX(1f).translationY(0f).setDuration(200L).start()
    }

    private fun collapseCenterMenu(nav: View, animate: Boolean) {
        val panel = nav.findViewById<View>(R.id.centerExpandPanel)
        val center = nav.findViewById<View>(R.id.btnLiquidScan)
        val overlay = nav.rootView.findViewById<View>(R.id.liquid_center_dismiss_overlay)
        if (panel.visibility != View.VISIBLE) return
        val endAction = {
            panel.visibility = View.GONE
            panel.alpha = 0f
            overlay?.visibility = View.GONE
            center.visibility = View.VISIBLE
            center.animate().alpha(1f).setDuration(140L).start()
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
