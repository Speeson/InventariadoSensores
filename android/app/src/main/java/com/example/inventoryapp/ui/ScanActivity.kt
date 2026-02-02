package com.example.inventoryapp.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageProxy
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityScanBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding

    private val cameraPermissionRequest = 1001
    private var lastScannedCode: String? = null
    private var isProcessing = false
    private var hasNavigated = false
    private var isValidating = false
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.previewView.visibility = android.view.View.GONE

        binding.btnActivateScanner.setOnClickListener {
            binding.btnActivateScanner.visibility = android.view.View.GONE
            binding.btnCloseScanner.visibility = android.view.View.VISIBLE
            binding.previewView.visibility = android.view.View.VISIBLE

            if (hasCameraPermission()) {
                startCamera()
            } else {
                requestCameraPermission()
            }
        }

        binding.btnCloseScanner.setOnClickListener {
            stopCamera()
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
                Toast.makeText(this, "Permiso de camara denegado", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, "Error al iniciar camara: ${e.message}", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        binding.previewView.visibility = android.view.View.GONE
        binding.btnActivateScanner.visibility = android.view.View.VISIBLE
        binding.btnCloseScanner.visibility = android.view.View.GONE
        isProcessing = false
        isValidating = false
        hasNavigated = false
        lastScannedCode = null
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
                    lastScannedCode = code
                    binding.etBarcode.setText(code)
                    Toast.makeText(this, "Detectado: $code", Toast.LENGTH_SHORT).show()
                    if (!hasNavigated) {
                        validateAndNavigate(code)
                    }
                }
            }
            .addOnFailureListener {
                // silencio
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
                    openConfirm(barcode)
                } else {
                    lastScannedCode = null
                    Toast.makeText(this@ScanActivity, "Producto no encontrado", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                lastScannedCode = null
                Toast.makeText(this@ScanActivity, "No se pudo validar el producto", Toast.LENGTH_LONG).show()
            } finally {
                isValidating = false
            }
        }
    }

    private fun openConfirm(barcode: String) {
        val i = Intent(this, ConfirmScanActivity::class.java)
        i.putExtra("barcode", barcode)
        startActivity(i)
    }
}
