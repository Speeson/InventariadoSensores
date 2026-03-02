package com.example.inventoryapp.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityScanBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.home.HomeActivity
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_CONFIRM = "extra_open_confirm"
        const val EXTRA_BARCODE = "extra_barcode"
        const val EXTRA_OFFLINE = "extra_offline"
        const val EXTRA_RETURN_HOME_ON_DIALOG_CLOSE = "extra_return_home_on_dialog_close"

        fun buildConfirmIntent(
            context: Context,
            barcode: String,
            offline: Boolean,
            returnHomeOnDialogClose: Boolean = false
        ): Intent {
            return Intent(context, ScanActivity::class.java)
                .putExtra(EXTRA_OPEN_CONFIRM, true)
                .putExtra(EXTRA_BARCODE, barcode)
                .putExtra(EXTRA_OFFLINE, offline)
                .putExtra(EXTRA_RETURN_HOME_ON_DIALOG_CLOSE, returnHomeOnDialogClose)
        }
    }

    private lateinit var binding: ActivityScanBinding

    private val cameraPermissionRequest = 1001
    private var lastScannedCode: String? = null
    private var isProcessing = false
    private var hasNavigated = false
    private var isValidating = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastNotFoundCode: String? = null
    private var lastNotFoundAt: Long = 0L
    private var returnHomeAfterDialogClose = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        applyScanTitleGradient()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        supportFragmentManager.setFragmentResultListener(
            ConfirmScanDialogFragment.REQUEST_KEY,
            this
        ) { _, _ ->
            onConfirmDialogClosed()
        }

        binding.previewFrame.visibility = View.GONE
        binding.etBarcode.clearFocus()
        binding.scanContentRoot.requestFocus()
        hideKeyboard()

        binding.btnActivateScanner.setOnClickListener {
            binding.btnActivateScanner.visibility = View.GONE
            binding.btnCloseScanner.visibility = View.VISIBLE
            binding.previewFrame.visibility = View.VISIBLE

            if (hasCameraPermission()) {
                startCamera()
            } else {
                requestCameraPermission()
            }
        }

        binding.btnCloseScanner.setOnClickListener {
            collapseCamera(resetSession = true)
        }

        binding.btnContinue.setOnClickListener {
            val manual = binding.etBarcode.text.toString().trim()
            val codeToUse = if (manual.isNotEmpty()) manual else (lastScannedCode ?: "")

            if (codeToUse.isEmpty()) {
                binding.etBarcode.error = "Codigo requerido"
                return@setOnClickListener
            }
            if (isValidating) return@setOnClickListener
            validateAndNavigate(codeToUse)
        }


        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }


    override fun onPause() {
        super.onPause()
        cameraProvider?.unbindAll()
        isProcessing = false
    }


    private fun handleLaunchIntent(startIntent: Intent?) {
        returnHomeAfterDialogClose =
            startIntent?.getBooleanExtra(EXTRA_RETURN_HOME_ON_DIALOG_CLOSE, false) == true

        val explicitBarcode = startIntent?.getStringExtra(EXTRA_BARCODE)?.trim().orEmpty()
        val legacyBarcode = startIntent?.getStringExtra("barcode")?.trim().orEmpty()
        val barcode = if (explicitBarcode.isNotBlank()) explicitBarcode else legacyBarcode

        val shouldOpenConfirm =
            (startIntent?.getBooleanExtra(EXTRA_OPEN_CONFIRM, false) == true) || barcode.isNotBlank()
        if (!shouldOpenConfirm || barcode.isBlank()) return

        val offline =
            startIntent?.getBooleanExtra(EXTRA_OFFLINE, false)
                ?: startIntent?.getBooleanExtra("offline", false)
                ?: false

        startIntent?.removeExtra(EXTRA_OPEN_CONFIRM)
        startIntent?.removeExtra(EXTRA_BARCODE)
        startIntent?.removeExtra(EXTRA_OFFLINE)
        startIntent?.removeExtra(EXTRA_RETURN_HOME_ON_DIALOG_CLOSE)
        startIntent?.removeExtra("barcode")
        startIntent?.removeExtra("offline")

        binding.etBarcode.setText(barcode)
        hasNavigated = true
        binding.scanScrollView.post {
            openConfirm(barcode, offline)
        }
    }

    private fun onConfirmDialogClosed() {
        if (returnHomeAfterDialogClose) {
            navigateToHome()
            return
        }

        hasNavigated = false
        isValidating = false
        lastScannedCode = null
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }


    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(binding.etBarcode.windowToken, 0)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            cameraPermissionRequest
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequest) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startCamera()
            } else {
                CreateUiFeedback.showErrorPopup(
                    activity = this,
                    title = "Permiso de camara denegado",
                    details = "",
                    animationRes = R.raw.camera
                )
                collapseCamera(resetSession = true)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                analyzeImage(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, preview, analysis)
            } catch (e: Exception) {
                UiNotifier.show(this, "Error al iniciar camara: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun collapseCamera(resetSession: Boolean) {
        cameraProvider?.unbindAll()
        binding.previewFrame.visibility = View.GONE
        binding.btnActivateScanner.visibility = View.VISIBLE
        binding.btnCloseScanner.visibility = View.GONE
        isProcessing = false
        isValidating = false
        if (resetSession) {
            hasNavigated = false
            lastScannedCode = null
            lastNotFoundCode = null
            lastNotFoundAt = 0L
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (isProcessing || isValidating) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val code = barcodes.firstOrNull()?.rawValue
                if (!code.isNullOrBlank() && code != lastScannedCode) {
                    if (code == lastNotFoundCode && (System.currentTimeMillis() - lastNotFoundAt) < 3000) {
                        return@addOnSuccessListener
                    }
                    lastScannedCode = code
                    binding.etBarcode.setText(code)
                    if (!hasNavigated) {
                        validateAndNavigate(code)
                    }
                }
            }
            .addOnFailureListener {
                // silence
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    private fun validateAndNavigate(barcode: String) {
        isValidating = true
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listProducts(barcode = barcode, limit = 1, offset = 0)
                val found = res.isSuccessful && res.body() != null && res.body()!!.items.isNotEmpty()
                if (found) {
                    hasNavigated = true
                    openConfirm(barcode, false)
                } else {
                    lastNotFoundCode = barcode
                    lastNotFoundAt = System.currentTimeMillis()
                    CreateUiFeedback.showErrorPopup(
                        activity = this@ScanActivity,
                        title = "Producto no encontrado",
                        details = "Detectado: $barcode",
                        animationRes = R.raw.notfound
                    )
                }
            } catch (_: Exception) {
                lastScannedCode = null
                UiNotifier.show(this@ScanActivity, "Sin conexion. Se enviara al reconectar")
                hasNavigated = true
                openConfirm(barcode, true)
            } finally {
                isValidating = false
            }
        }
    }

    private fun openConfirm(barcode: String, offline: Boolean) {
        collapseCamera(resetSession = false)
        if (supportFragmentManager.findFragmentByTag(ConfirmScanDialogFragment.TAG) != null) {
            return
        }
        ConfirmScanDialogFragment.newInstance(barcode, offline)
            .show(supportFragmentManager, ConfirmScanDialogFragment.TAG)
    }

    private fun applyScanTitleGradient() {
        binding.tvScanTitle.post {
            val paint = binding.tvScanTitle.paint
            val width = paint.measureText(binding.tvScanTitle.text.toString())
            if (width <= 0f) return@post
            val c1 = ContextCompat.getColor(this, R.color.icon_grad_start)
            val c2 = ContextCompat.getColor(this, R.color.icon_grad_mid2)
            val c3 = ContextCompat.getColor(this, R.color.icon_grad_mid1)
            val c4 = ContextCompat.getColor(this, R.color.icon_grad_end)
            val shader = android.graphics.LinearGradient(
                0f,
                0f,
                width,
                0f,
                intArrayOf(c1, c2, c3, c4),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            binding.tvScanTitle.invalidate()
        }
    }
}


