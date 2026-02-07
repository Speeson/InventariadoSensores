package com.example.inventoryapp.ui.products

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityLabelPreviewBinding
import com.example.inventoryapp.ui.common.UiNotifier
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.widget.Button
import com.example.inventoryapp.R

class LabelPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLabelPreviewBinding
    private var productId: Int = 0
    private var sku: String = ""
    private var barcode: String = ""
    private var lastSvg: String? = null
    private val niimbotPackage = "com.gengcon.android.jccloudprinter"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabelPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productId = intent.getIntExtra("product_id", 0)
        sku = intent.getStringExtra("product_sku") ?: ""
        barcode = intent.getStringExtra("product_barcode") ?: ""

        binding.btnBack.setOnClickListener { finish() }
        binding.tvTitle.text = "Etiqueta"
        binding.tvMeta.text = "SKU $sku  �  Barcode $barcode"

        setupWebView()

        binding.btnDownloadSvg.setOnClickListener { downloadSvg() }
        binding.btnDownloadPdf.setOnClickListener { downloadPdf() }
        binding.btnRegenerate.setOnClickListener { regenerateLabel() }
        binding.btnPrint.setOnClickListener { printLabel() }
        val btnSaveNiimbot = binding.root.findViewById<Button>(R.id.btnSaveNiimbot)
        btnSaveNiimbot?.setOnClickListener { saveNiimbotLabel() }

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

    private fun saveNiimbotLabel() {
        val svg = lastSvg
        if (svg.isNullOrBlank()) {
            UiNotifier.show(this, "Etiqueta no disponible")
            return
        }
        UiNotifier.show(this, "Guardando etiqueta...")
        val widthPx = 400
        val heightPx = 240
        binding.webLabel.post {
            val preview = capturePreviewBitmap(widthPx, heightPx)
            if (preview == null) {
                UiNotifier.show(this, "No se pudo capturar la etiqueta")
                return@post
            }
            val filename = "label_${sku.ifBlank { productId.toString() }}_niimbot.png"
            if (saveToPictures(filename, preview)) {
                UiNotifier.show(this, "Etiqueta guardada en galería")
                openNiimbotApp()
            } else {
                UiNotifier.show(this, "No se pudo guardar la etiqueta")
            }
        }
    }

    private fun toMonochrome(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val lum = (0.299 * r + 0.587 * g + 0.114 * b)
                val bw = if (lum < 128) Color.BLACK else Color.WHITE
                out.setPixel(x, y, bw)
            }
        }
        return out
    }

    private fun capturePreviewBitmap(targetW: Int, targetH: Int): Bitmap? {
        val view = binding.webLabel
        if (view.width <= 0 || view.height <= 0) return null
        val src = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return Bitmap.createScaledBitmap(src, targetW, targetH, false)
    }

    private fun openNiimbotApp() {
        try {
            val explicit = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(
                    "com.gengcon.android.jccloudprinter",
                    "com.gengcon.android.jccloudprinter.LaunchActivity"
                )
            }
            if (explicit.resolveActivity(packageManager) != null) {
                UiNotifier.show(this, "Abriendo Niimbot…")
                startActivity(explicit)
                return
            }

            val explicitAlt = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(
                    "com.gengcon.android.jccloudprinter",
                    "com.gengcon.android.jccloudprinter.LaunchFromWebActivity"
                )
            }
            if (explicitAlt.resolveActivity(packageManager) != null) {
                UiNotifier.show(this, "Abriendo Niimbot…")
                startActivity(explicitAlt)
                return
            }

            val intent = packageManager.getLaunchIntentForPackage(niimbotPackage)
            if (intent != null) {
                UiNotifier.show(this, "Abriendo Niimbot…")
                startActivity(intent)
                return
            }

            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val candidates = packageManager.queryIntentActivities(
                launcherIntent.setPackage(niimbotPackage),
                0
            )
            if (candidates.isNotEmpty()) {
                val activity = candidates[0].activityInfo
                val component = ComponentName(activity.packageName, activity.name)
                val explicitIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                explicitIntent.component = component
                startActivity(explicitIntent)
                return
            }

            val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$niimbotPackage"))
            startActivity(settingsIntent)
            UiNotifier.show(this, "No se encontró el launcher de Niimbot")
        } catch (_: Exception) {
            UiNotifier.show(this, "No se pudo abrir la app de Niimbot")
        }
    }

    private fun saveToPictures(filename: String, bitmap: Bitmap): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IoTrack/labels")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    UiNotifier.show(this, "No se pudo crear el archivo en la galería")
                    return false
                }
                val outStream = resolver.openOutputStream(uri)
                if (outStream == null) {
                    UiNotifier.show(this, "No se pudo abrir el archivo en la galería")
                    return false
                }
                outStream.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "IoTrack/labels")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        } catch (e: Exception) {
            UiNotifier.show(this, "Error guardando etiqueta: ${e.message}")
            false
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

