package com.example.inventoryapp.ui.products

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityLabelPreviewBinding
import com.example.inventoryapp.ui.common.UiNotifier
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class LabelPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLabelPreviewBinding
    private var productId: Int = 0
    private var sku: String = ""
    private var barcode: String = ""
    private var lastSvg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabelPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productId = intent.getIntExtra("product_id", 0)
        sku = intent.getStringExtra("product_sku") ?: ""
        barcode = intent.getStringExtra("product_barcode") ?: ""

        binding.btnBack.setOnClickListener { finish() }
        binding.tvTitle.text = "Etiqueta"
        binding.tvMeta.text = "SKU $sku  •  Barcode $barcode"

        setupWebView()

        binding.btnDownloadSvg.setOnClickListener { downloadSvg() }
        binding.btnDownloadPdf.setOnClickListener { downloadPdf() }
        binding.btnRegenerate.setOnClickListener { regenerateLabel() }
        binding.btnPrint.setOnClickListener { printLabel() }

        checkRoleForRegenerate()
        loadLabel()
    }

    private fun setupWebView() {
        binding.webLabel.settings.javaScriptEnabled = false
        binding.webLabel.settings.loadWithOverviewMode = true
        binding.webLabel.settings.useWideViewPort = true
        binding.webLabel.settings.builtInZoomControls = false
        binding.webLabel.settings.displayZoomControls = false
        binding.webLabel.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        binding.webLabel.clearCache(true)
        binding.webLabel.webViewClient = WebViewClient()
    }

    private fun loadLabel() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getProductLabelSvg(productId)
                if (res.isSuccessful && res.body() != null) {
                    val svg = res.body()!!.string()
                    lastSvg = svg
                    val svgB64 = Base64.encodeToString(svg.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    val html = """
                        <!doctype html>
                        <html>
                        <head>
                          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                          <style>
                            html, body { margin:0; padding:0; width:100%; height:100%; }
                            body { display:flex; align-items:center; justify-content:center; background:#fff; }
                            .label { width:100%; max-width:900px; }
                            .label img { width:100%; height:auto; display:block; }
                          </style>
                        </head>
                        <body>
                          <div class="label">
                            <img src="data:image/svg+xml;base64,$svgB64" alt="label" />
                          </div>
                        </body>
                        </html>
                    """.trimIndent()
                    binding.webLabel.loadDataWithBaseURL(
                        null,
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                } else {
                    UiNotifier.show(this@LabelPreviewActivity, "No se pudo cargar la etiqueta")
                }
            } catch (e: Exception) {
                UiNotifier.show(this@LabelPreviewActivity, "Error cargando etiqueta: ${e.message}")
            }
        }
    }

    private fun downloadSvg() {
        val svg = lastSvg
        if (svg.isNullOrBlank()) {
            UiNotifier.show(this, "Etiqueta no disponible")
            return
        }
        val bytes = svg.toByteArray(Charsets.UTF_8)
        val filename = "label_${sku.ifBlank { productId.toString() }}.svg"
        if (saveToDownloads(filename, "image/svg+xml", bytes)) {
            UiNotifier.show(this, "SVG guardado en descargas")
        } else {
            UiNotifier.show(this, "No se pudo guardar el SVG")
        }
    }

    private fun downloadPdf() {
        val svg = lastSvg
        if (svg.isNullOrBlank()) {
            UiNotifier.show(this, "Etiqueta no disponible")
            return
        }
        binding.webLabel.post {
            val pdfBytes = WebViewPdfExporter.export(binding.webLabel)
            if (pdfBytes == null) {
                UiNotifier.show(this, "No se pudo generar el PDF")
                return@post
            }
            val filename = "label_${sku.ifBlank { productId.toString() }}.pdf"
            if (saveToDownloads(filename, "application/pdf", pdfBytes)) {
                UiNotifier.show(this, "PDF guardado en descargas")
            } else {
                UiNotifier.show(this, "No se pudo guardar el PDF")
            }
        }
    }

    private fun printLabel() {
        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val adapter = binding.webLabel.createPrintDocumentAdapter("label_$productId")
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print("Etiqueta_$productId", adapter, attrs)
    }

    private fun regenerateLabel() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.regenerateProductLabel(productId)
                if (res.isSuccessful) {
                    UiNotifier.show(this@LabelPreviewActivity, "Etiqueta regenerada")
                    loadLabel()
                } else {
                    UiNotifier.show(this@LabelPreviewActivity, "No se pudo regenerar la etiqueta")
                }
            } catch (e: Exception) {
                UiNotifier.show(this@LabelPreviewActivity, "Error regenerando etiqueta: ${e.message}")
            }
        }
    }

    private fun checkRoleForRegenerate() {
        binding.btnRegenerate.visibility = android.view.View.GONE
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.me()
                if (res.isSuccessful && res.body() != null) {
                    val role = res.body()!!.role.uppercase()
                    applyRegenerateVisibility(role)
                }
            } catch (_: Exception) {
                // leave hidden
            }
        }
    }

    private fun applyRegenerateVisibility(role: String) {
        if (role == "MANAGER" || role == "ADMIN") {
            binding.btnRegenerate.visibility = android.view.View.VISIBLE
            binding.btnRegenerate.isEnabled = true
            binding.btnRegenerate.alpha = 1.0f
            binding.btnRegenerate.text = "Regenerar etiqueta"
            binding.btnRegenerate.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            return
        }

        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val showRestricted = prefs.getBoolean("show_restricted_cards", false)
        if (!showRestricted) {
            binding.btnRegenerate.visibility = android.view.View.GONE
            return
        }

        binding.btnRegenerate.visibility = android.view.View.VISIBLE
        binding.btnRegenerate.isEnabled = false
        binding.btnRegenerate.alpha = 0.6f
        binding.btnRegenerate.text = "Solo admin/manager"
        binding.btnRegenerate.setCompoundDrawablesWithIntrinsicBounds(
            com.example.inventoryapp.R.drawable.ic_lock,
            0,
            0,
            0
        )
        binding.btnRegenerate.compoundDrawablePadding = 12
    }

    private fun saveToDownloads(filename: String, mime: String, bytes: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(cacheDir, "downloads")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { it.write(bytes) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

private object WebViewPdfExporter {
    fun export(webView: WebView): ByteArray? {
        return try {
            val width = webView.width
            val height = webView.height
            if (width <= 0 || height <= 0) return null
            val document = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = document.startPage(pageInfo)
            webView.draw(page.canvas)
            document.finishPage(page)
            val out = java.io.ByteArrayOutputStream()
            document.writeTo(out)
            document.close()
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
