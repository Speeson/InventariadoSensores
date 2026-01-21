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
import com.example.inventoryapp.databinding.ActivityScanBinding
import com.example.inventoryapp.ui.movements.ConfirmMovementActivity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.ImageProxy
import androidx.camera.core.ExperimentalGetImage


class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding

    private val cameraPermissionRequest = 1001
    private var lastScannedCode: String? = null
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón continuar: usa el manual, o el último detectado
        binding.btnContinue.setOnClickListener {
            val manual = binding.etBarcode.text.toString().trim()
            val codeToUse = if (manual.isNotEmpty()) manual else (lastScannedCode ?: "")

            if (codeToUse.isEmpty()) {
                binding.etBarcode.error = "Código requerido"
                return@setOnClickListener
            }

            val i = Intent(this, ConfirmMovementActivity::class.java)
            i.putExtra("barcode", codeToUse)
            startActivity(i)
        }

        // Arrancar cámara (si hay permiso)
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
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
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

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
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al iniciar cámara: ${e.message}", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (isProcessing) {
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

                    // Rellenar el campo manual para que el usuario lo vea
                    binding.etBarcode.setText(code)

                    Toast.makeText(this, "Detectado: $code", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                // Silencioso por ahora (para Sprint 1)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }
}
