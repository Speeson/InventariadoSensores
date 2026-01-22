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
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.databinding.ActivityScanBinding
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateRequest
import com.example.inventoryapp.ui.movements.ConfirmMovementActivity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.ImageProxy
import androidx.camera.core.ExperimentalGetImage
import kotlinx.coroutines.launch

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

            submitScan(codeToUse)
        }

        // Arrancar cámara (si hay permiso)
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun submitScan(barcode: String) {
        lifecycleScope.launch {
            try {
                // 1) Buscar producto por barcode
                val prodResp = NetworkModule.api.listProducts(
                    barcode = barcode,
                    limit = 1,
                    offset = 0
                )

                if (!prodResp.isSuccessful || prodResp.body() == null || prodResp.body()!!.items.isEmpty()) {
                    Toast.makeText(
                        this@ScanActivity,
                        "Producto no encontrado para barcode: $barcode",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val productId = prodResp.body()!!.items.first().id

                // 2) Crear evento
                val eventReq = EventCreateRequest(
                    event_type = "SENSOR_IN", // cambia a "SENSOR_OUT" si es salida
                    product_id = productId,
                    delta = 1,
                    source = "SCAN",
                    location = "default"
                )

                val eventResp = NetworkModule.api.createEvent(eventReq)

                if (!eventResp.isSuccessful || eventResp.body() == null) {
                    Toast.makeText(
                        this@ScanActivity,
                        "Error creando evento: ${eventResp.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                Toast.makeText(this@ScanActivity, "Evento creado ✅", Toast.LENGTH_SHORT).show()

                // 3) Navegar a confirmación
                val i = Intent(this@ScanActivity, ConfirmMovementActivity::class.java)
                i.putExtra("barcode", barcode)
                i.putExtra("product_id", productId)
                i.putExtra("event_id", eventResp.body()!!.id)
                startActivity(i)

            } catch (e: Exception) {
                Toast.makeText(this@ScanActivity, "Fallo de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
