package com.example.inventoryapp.ui.common

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
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
        MANUAL,
        SCAN,
        SEARCH,
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

    private val excluded = setOf(
        "com.example.inventoryapp.ui.auth.LoginActivity",
        "com.example.inventoryapp.ui.alerts.AlertsActivity",
    )

    private val quickTargets = listOf(
        SearchTarget("Home", ACTION_HOME, HomeActivity::class.java),
        SearchTarget("Productos", "products", ProductListActivity::class.java),
        SearchTarget("Stock", "stock", StockActivity::class.java),
        SearchTarget("Movimientos", "movements", MovementsMenuActivity::class.java),
        SearchTarget("Eventos", "events", EventsActivity::class.java),
        SearchTarget("Escanear", ACTION_SCAN),
        SearchTarget("Manual codigo", ACTION_MANUAL),
        SearchTarget("Categorias", "categories", CategoriesActivity::class.java),
        SearchTarget("Umbrales", "thresholds", ThresholdsActivity::class.java),
        SearchTarget("Importaciones", "imports", ImportsActivity::class.java),
        SearchTarget("Reportes", "reports", ReportsActivity::class.java),
        SearchTarget("Rotacion", "rotation", RotationActivity::class.java),
        SearchTarget("Auditoria", "audit", AuditActivity::class.java),
        SearchTarget("Perfil", ACTION_PROFILE),
    )

    fun install(activity: AppCompatActivity) {
        if (excluded.contains(activity.javaClass.name)) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewById<View>(R.id.liquidBottomNavRoot) != null) return
        content.setBackgroundResource(R.drawable.bg_home_gradient)

        val originalChild = content.getChildAt(0) ?: return
        val nav = LayoutInflater.from(activity).inflate(R.layout.nav_liquid_bottom_bar, content, false)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        content.addView(nav, params)

        bindActions(activity, nav)
        applyIconStyle(nav)
        highlightCurrentTab(activity, nav)

        nav.post {
            ensureBottomSpace(originalChild, nav)
        }
    }

    private fun bindActions(activity: AppCompatActivity, nav: View) {
        nav.findViewById<ImageButton>(R.id.btnLiquidHome).setOnClickListener {
            open(activity, HomeActivity::class.java)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidManual).setOnClickListener {
            showManualPopup(activity)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidScan).setOnClickListener {
            showScanPopup(activity)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidSearch).setOnClickListener {
            showQuickSearch(activity)
        }
        nav.findViewById<ImageButton>(R.id.btnLiquidProfile).setOnClickListener {
            showProfilePopup(activity)
        }
    }

    private fun open(activity: AppCompatActivity, target: Class<out Activity>) {
        if (activity::class.java == target) return
        val intent = Intent(activity, target)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activity.startActivity(intent)
    }

    private fun ensureBottomSpace(contentRoot: View, nav: View) {
        val initial = (contentRoot.getTag(R.id.tag_liquid_nav_original_padding_bottom) as? Int)
            ?: contentRoot.paddingBottom.also {
                contentRoot.setTag(R.id.tag_liquid_nav_original_padding_bottom, it)
            }

        val extra = nav.height + dp(nav, 8)
        val targetBottom = initial + extra
        if (contentRoot.paddingBottom < targetBottom) {
            contentRoot.updatePadding(bottom = targetBottom)
        }
    }

    private fun applyIconStyle(nav: View) {
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidHome), R.drawable.home)
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidManual), R.drawable.code_manual)
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidScan), R.drawable.scaner)
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidSearch), R.drawable.search)
        GradientIconUtil.applyGradient(nav.findViewById<ImageButton>(R.id.btnLiquidProfile), R.drawable.user)
    }

    private fun highlightCurrentTab(activity: AppCompatActivity, nav: View) {
        val tab = currentTab(activity)
        setSelected(nav.findViewById(R.id.btnLiquidHome), tab == NavTab.HOME)
        setSelected(nav.findViewById(R.id.btnLiquidManual), tab == NavTab.MANUAL)
        setSelected(nav.findViewById(R.id.btnLiquidScan), tab == NavTab.SCAN)
        setSelected(nav.findViewById(R.id.btnLiquidSearch), tab == NavTab.SEARCH)
        setSelected(nav.findViewById(R.id.btnLiquidProfile), tab == NavTab.PROFILE)
    }

    private fun currentTab(activity: AppCompatActivity): NavTab {
        return when (activity) {
            is HomeActivity -> NavTab.HOME
            is ConfirmScanActivity -> NavTab.SCAN
            else -> NavTab.NONE
        }
    }

    private fun setSelected(button: ImageButton, selected: Boolean) {
        val isCenter = button.id == R.id.btnLiquidScan
        if (isCenter) {
            button.setBackgroundResource(R.drawable.bg_liquid_center_square)
            button.imageAlpha = if (selected) 255 else 235
            return
        }
        if (selected) {
            button.setBackgroundResource(R.drawable.bg_liquid_icon_selected)
            button.imageAlpha = 255
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.imageAlpha = 230
        }
    }

    private fun showQuickSearch(activity: AppCompatActivity) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_quick_nav_search, null)
        val input = dialogView.findViewById<EditText>(R.id.etQuickNavSearch)
        val list = dialogView.findViewById<ListView>(R.id.lvQuickNav)

        val labels = quickTargets.map { it.label }
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, labels.toMutableList())
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

        styleDialog(dialog)
    }

    private fun dispatchTarget(activity: AppCompatActivity, target: SearchTarget) {
        when (target.action) {
            ACTION_HOME -> open(activity, HomeActivity::class.java)
            ACTION_MANUAL -> showManualPopup(activity)
            ACTION_SCAN -> showScanPopup(activity)
            ACTION_PROFILE -> showProfilePopup(activity)
            else -> target.activityClass?.let { open(activity, it) }
        }
    }

    private fun showManualPopup(activity: AppCompatActivity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_manual, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        val input = view.findViewById<EditText>(R.id.etManualBarcode)
        view.findViewById<Button>(R.id.btnManualCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnManualAccept).setOnClickListener {
            val code = input.text?.toString()?.trim().orEmpty()
            if (code.isBlank()) {
                input.error = "Codigo requerido"
                return@setOnClickListener
            }
            dialog.dismiss()
            val intent = Intent(activity, ConfirmScanActivity::class.java)
                .putExtra("barcode", code)
                .putExtra("offline", false)
            activity.startActivity(intent)
        }
        styleDialog(dialog)
    }

    private fun showScanPopup(activity: AppCompatActivity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_scan, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        view.findViewById<Button>(R.id.btnScanPopupCancel).setOnClickListener {
            dialog.dismiss()
            showScanCancelWarning(activity)
        }
        view.findViewById<Button>(R.id.btnScanPopupOpen).setOnClickListener {
            dialog.dismiss()
            if (!hasCameraPermission(activity)) {
                showScanCancelWarning(activity)
                return@setOnClickListener
            }
            showQuickScanCameraPopup(activity)
        }
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

    private fun showScanCancelWarning(activity: AppCompatActivity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_scan_warning, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        view.findViewById<Button>(R.id.btnScanWarnClose).setOnClickListener { dialog.dismiss() }
        styleDialog(dialog)
    }

    private fun showProfilePopup(activity: AppCompatActivity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_liquid_profile, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        view.findViewById<ImageView>(R.id.ivProfileIconPopup)?.let {
            GradientIconUtil.applyGradient(it, R.drawable.user)
        }
        val closeBtn = view.findViewById<ImageButton>(R.id.btnProfilePopupClose)
        closeBtn.setOnClickListener { dialog.dismiss() }
        styleDialog(dialog, widthPercent = 0.9f)
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

