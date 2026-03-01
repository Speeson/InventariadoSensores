package com.example.inventoryapp.ui.products

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContentValues
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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
import androidx.fragment.app.DialogFragment
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityLabelPreviewBinding
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class LabelPreviewDialogFragment : DialogFragment() {

    private var _binding: ActivityLabelPreviewBinding? = null
    private val binding get() = _binding!!
    private var productId: Int = 0
    private var sku: String = ""
    private var barcode: String = ""
    private var lastSvg: String? = null
    private val niimbotPackage = "com.gengcon.android.jccloudprinter"
    private var niimbotScanDialog: AlertDialog? = null
    private var niimbotPrintDialog: AlertDialog? = null
    private var saveLabelLoadingHandle: CreateUiFeedback.LoadingHandle? = null
    private var btReceiverRegistered = false
    private val discoveredDevices = linkedMapOf<String, BluetoothDevice>()
    private val discoveredRows = mutableListOf<String>()
    private var discoveredAdapter: android.widget.ArrayAdapter<String>? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val niimbotPrefs by lazy { requireContext().getSharedPreferences("niimbot_prefs", Context.MODE_PRIVATE) }

    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        private const val ARG_PRODUCT_SKU = "product_sku"
        private const val ARG_PRODUCT_BARCODE = "product_barcode"
        private const val REQ_BT_PERMS = 1107
        private const val KEY_LAST_NIIMBOT_MAC = "last_niimbot_mac"
        private const val KEY_LAST_NIIMBOT_NAME = "last_niimbot_name"

        fun newInstance(productId: Int, sku: String, barcode: String): LabelPreviewDialogFragment {
            return LabelPreviewDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PRODUCT_ID, productId)
                    putString(ARG_PRODUCT_SKU, sku)
                    putString(ARG_PRODUCT_BARCODE, barcode)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_Inventoryapp_LabelPreviewPopup)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        _binding = ActivityLabelPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        productId = args.getInt(ARG_PRODUCT_ID, 0)
        sku = args.getString(ARG_PRODUCT_SKU).orEmpty()
        barcode = args.getString(ARG_PRODUCT_BARCODE).orEmpty()

        if (productId <= 0) {
            dismissAllowingStateLoss()
            return
        }

        binding.btnBack.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvTitle.text = "Etiqueta"
        binding.tvMeta.text = "SKU $sku  -  Barcode $barcode"
        setupWebView()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        binding.btnDownloadSvg.setOnClickListener { downloadSvg() }
        binding.btnDownloadPdf.setOnClickListener { downloadPdf() }
        binding.btnRegenerate.setOnClickListener { regenerateLabel() }
        binding.btnPrint.setOnClickListener { printLabel() }
        binding.btnSaveNiimbot.setOnClickListener { showNiimbotActionsDialog() }

        checkRoleForRegenerate()
        loadLabel()
    }

    override fun onStart() {
        super.onStart()
        val width = (resources.displayMetrics.widthPixels * 0.94f).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.92f).toInt()
        dialog?.window?.setLayout(width, height)
    }
    override fun onResume() {
        super.onResume()
        val currentUrl = binding.webLabel.url?.trim().orEmpty()
        if (currentUrl == "about:blank") {
            val cached = lastSvg
            if (!cached.isNullOrBlank()) {
                renderSvgToWebView(cached)
            } else {
                loadLabel()
            }
        }
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
                    renderSvgToWebView(svg)
                } else {
                    UiNotifier.show(requireActivity(), "No se pudo cargar la etiqueta")
                }
            } catch (e: Exception) {
                UiNotifier.show(requireActivity(), "Error cargando etiqueta: ${e.message}")
            }
        }
    }

    private fun renderSvgToWebView(svg: String) {
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
    }

    private fun downloadSvg() {
        val svg = lastSvg
        if (svg.isNullOrBlank()) {
            UiNotifier.show(requireActivity(), "Etiqueta no disponible")
            return
        }
        val bytes = svg.toByteArray(Charsets.UTF_8)
        val filename = "label_${sku.ifBlank { productId.toString() }}.svg"
        if (saveToDownloads(filename, "image/svg+xml", bytes)) {
            UiNotifier.show(requireActivity(), "SVG guardado en descargas")
        } else {
            UiNotifier.show(requireActivity(), "No se pudo guardar el SVG")
        }
    }

    private fun downloadPdf() {
        val svg = lastSvg
        if (svg.isNullOrBlank()) {
            UiNotifier.show(requireActivity(), "Etiqueta no disponible")
            return
        }
        binding.webLabel.post {
            val pdfBytes = WebViewPdfExporter.export(binding.webLabel)
            if (pdfBytes == null) {
                UiNotifier.show(requireActivity(), "No se pudo generar el PDF")
                return@post
            }
            val filename = "label_${sku.ifBlank { productId.toString() }}.pdf"
            if (saveToDownloads(filename, "application/pdf", pdfBytes)) {
                UiNotifier.show(requireActivity(), "PDF guardado en descargas")
            } else {
                UiNotifier.show(requireActivity(), "No se pudo guardar el PDF")
            }
        }
    }

    private fun printLabel() {
        val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
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
                    UiNotifier.show(requireActivity(), "Etiqueta regenerada")
                    loadLabel()
                } else {
                    UiNotifier.show(requireActivity(), "No se pudo regenerar la etiqueta")
                }
            } catch (e: Exception) {
                UiNotifier.show(requireActivity(), "Error regenerando etiqueta: ${e.message}")
            }
        }
    }

    private fun saveNiimbotLabel() {
        val svg = lastSvg
        if (svg.isNullOrBlank()) {
            UiNotifier.show(requireActivity(), "Etiqueta no disponible")
            return
        }
        saveLabelLoadingHandle?.dismiss()
        saveLabelLoadingHandle = CreateUiFeedback.showListLoading(
            activity = requireActivity(),
            message = "Guardando etiqueta...",
            animationRes = R.raw.glass_loading_list,
            minCycles = 1
        )
        tryCaptureAndSaveLabel(attemptsLeft = 6)
    }

    private fun showNiimbotActionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_niimbot_actions, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        view.findViewById<android.view.View>(R.id.btnNiimbotActionsClose)?.setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<android.view.View>(R.id.btnOpenNiimbotAppCard)?.setOnClickListener {
            dialog.dismiss()
            saveNiimbotLabel()
        }
        view.findViewById<android.view.View>(R.id.btnPrintNiimbotDirectCard)?.setOnClickListener {
            dialog.dismiss()
            startDirectNiimbotPrintFlow()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun tryCaptureAndSaveLabel(attemptsLeft: Int) {
        binding.webLabel.postDelayed({
            val raw = capturePreviewBitmap()
            if (raw == null) {
                if (attemptsLeft > 0) {
                    tryCaptureAndSaveLabel(attemptsLeft - 1)
                } else {
                    saveLabelLoadingHandle?.dismiss()
                    saveLabelLoadingHandle = null
                    UiNotifier.show(requireActivity(), "La etiqueta aun no esta lista")
                }
                return@postDelayed
            }
            val trimmed = trimVerticalWhitespace(raw)
            val filename = "label_${sku.ifBlank { productId.toString() }}_niimbot.png"
            if (saveToPictures(filename, trimmed)) {
                saveLabelLoadingHandle?.dismiss()
                saveLabelLoadingHandle = null
                UiNotifier.show(requireActivity(), "Etiqueta guardada en galeria")
                openNiimbotApp()
            } else {
                saveLabelLoadingHandle?.dismiss()
                saveLabelLoadingHandle = null
                UiNotifier.show(requireActivity(), "No se pudo guardar la etiqueta")
            }
        }, 150)
    }

    private fun capturePreviewBitmap(): Bitmap? {
        val view = binding.webLabel
        if (view.width <= 0 || view.height <= 0) return null
        val src = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return src
    }

    private fun trimVerticalWhitespace(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val threshold = 250
        val margin = 14
        var top = 0
        var bottom = h - 1
        run {
            var y = 0
            while (y < h) {
                var x = 0
                var found = false
                while (x < w) {
                    val c = src.getPixel(x, y)
                    if (Color.red(c) < threshold || Color.green(c) < threshold || Color.blue(c) < threshold) {
                        found = true
                        break
                    }
                    x++
                }
                if (found) {
                    top = (y - margin).coerceAtLeast(0)
                    break
                }
                y++
            }
        }
        run {
            var y = h - 1
            while (y >= top) {
                var x = 0
                var found = false
                while (x < w) {
                    val c = src.getPixel(x, y)
                    if (Color.red(c) < threshold || Color.green(c) < threshold || Color.blue(c) < threshold) {
                        found = true
                        break
                    }
                    x++
                }
                if (found) {
                    bottom = (y + margin).coerceAtMost(h - 1)
                    break
                }
                y--
            }
        }
        val cropH = (bottom - top + 1).coerceAtLeast(1)
        return Bitmap.createBitmap(src, 0, top, w, cropH)
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
            if (explicit.resolveActivity(requireContext().packageManager) != null) {
                UiNotifier.show(requireActivity(), "Abriendo NiimbotÃ¢â‚¬Â¦")
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
            if (explicitAlt.resolveActivity(requireContext().packageManager) != null) {
                UiNotifier.show(requireActivity(), "Abriendo NiimbotÃ¢â‚¬Â¦")
                startActivity(explicitAlt)
                return
            }

            val intent = requireContext().packageManager.getLaunchIntentForPackage(niimbotPackage)
            if (intent != null) {
                UiNotifier.show(requireActivity(), "Abriendo NiimbotÃ¢â‚¬Â¦")
                startActivity(intent)
                return
            }

            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val candidates = requireContext().packageManager.queryIntentActivities(
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
            UiNotifier.show(requireActivity(), "No se encontrÃƒÂ³ el launcher de Niimbot")
        } catch (e: Exception) {
            CreateUiFeedback.showErrorPopup(
                activity = requireActivity(),
                title = "No se pudo abrir Niimbot",
                details = e.message ?: "No se pudo abrir la app de Niimbot",
                animationRes = R.raw.wrong
            )
        }
    }

    private val btScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) addDiscoveredDevice(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (discoveredRows.isEmpty()) {
                        discoveredRows.add("No se encontraron impresoras. Pulsa Reescanear.")
                        discoveredAdapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun startDirectNiimbotPrintFlow() {
        if (lastSvg.isNullOrBlank()) {
            UiNotifier.show(requireActivity(), "Etiqueta no disponible")
            return
        }
        if (!ensureBluetoothReady()) return
        if (tryPrintWithRememberedPrinter()) return
        showBluetoothScanDialog()
        startBluetoothDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun tryPrintWithRememberedPrinter(): Boolean {
        val mac = niimbotPrefs.getString(KEY_LAST_NIIMBOT_MAC, null)?.trim().orEmpty()
        if (mac.isBlank()) return false
        val adapter = bluetoothAdapter ?: return false
        val rememberedDevice = try {
            adapter.getRemoteDevice(mac)
        } catch (_: Exception) {
            null
        } ?: return false
        connectAndPrint(rememberedDevice, fallbackToScanOnFailure = true)
        return true
    }

    private fun ensureBluetoothReady(): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            UiNotifier.show(requireActivity(), "Bluetooth no disponible")
            return false
        }
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return false
        }
        if (!adapter.isEnabled) {
            CreateUiFeedback.showStatusPopup(
                activity = requireActivity(),
                title = "Bluetooth desactivado",
                details = "Activa Bluetooth para conectar con Niimbot",
                animationRes = R.raw.bluetooth,
                autoDismissMs = 3000L
            )
            return false
        }
        return true
    }

    private fun hasBluetoothPermissions(): Boolean {
        val connectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        val scanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        val locationGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return connectGranted && scanGranted && locationGranted
    }

    private fun requestBluetoothPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.BLUETOOTH
            perms += Manifest.permission.BLUETOOTH_ADMIN
        }
        requestPermissions(perms.toTypedArray(), REQ_BT_PERMS)
    }

    @SuppressLint("MissingPermission")
    private fun showBluetoothScanDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_niimbot_bluetooth, null)
        val list = view.findViewById<android.widget.ListView>(R.id.lvBluetoothDevices)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btnBluetoothCancel)
        val btnRescan = view.findViewById<android.widget.Button>(R.id.btnBluetoothRescan)

        discoveredRows.clear()
        discoveredAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            discoveredRows
        )
        list.adapter = discoveredAdapter
        list.setOnItemClickListener { _, _, position, _ ->
            val text = discoveredRows.getOrNull(position).orEmpty()
            val device = discoveredDevices.values.firstOrNull { rowForDevice(it) == text }
            if (device != null) {
                niimbotScanDialog?.dismiss()
                connectAndPrint(device)
            }
        }

        btnRescan.setOnClickListener { startBluetoothDiscovery() }
        btnCancel.setOnClickListener { niimbotScanDialog?.dismiss() }

        niimbotScanDialog?.dismiss()
        niimbotScanDialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
        niimbotScanDialog?.setOnDismissListener {
            stopBluetoothDiscovery()
        }
        niimbotScanDialog?.show()
        niimbotScanDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        if (!hasBluetoothPermissions()) return
        if (!btReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            requireContext().registerReceiver(btScanReceiver, filter)
            btReceiverRegistered = true
        }

        discoveredDevices.clear()
        discoveredRows.clear()
        discoveredAdapter?.notifyDataSetChanged()

        val adapter = bluetoothAdapter ?: return
        adapter.cancelDiscovery()
        val bonded = adapter.bondedDevices.orEmpty()
        bonded.forEach { addDiscoveredDevice(it) }
        adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun stopBluetoothDiscovery() {
        val adapter = bluetoothAdapter
        if (adapter != null) {
            try {
                val canQueryDiscovery =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasBluetoothPermissions()
                if (canQueryDiscovery && adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (_: SecurityException) {
                // On some real devices the permission state can change while finishing.
                // Avoid crashing activity teardown if BLUETOOTH_SCAN is denied.
            } catch (_: Exception) {
                // Best-effort cleanup only.
            }
        }
        if (btReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(btScanReceiver)
            } catch (_: Exception) {}
            btReceiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDiscoveredDevice(device: BluetoothDevice) {
        val address = device.address ?: return
        if (address.isBlank()) return
        discoveredDevices[address] = device
        discoveredRows.clear()
        discoveredRows.addAll(
            discoveredDevices.values
                .sortedWith(
                    compareByDescending<BluetoothDevice> {
                        when (it.bondState) {
                            BluetoothDevice.BOND_BONDED -> 2
                            BluetoothDevice.BOND_BONDING -> 1
                            else -> 0
                        }
                    }.thenBy { (it.name ?: "").uppercase(Locale.getDefault()) }
                )
                .map { rowForDevice(it) }
        )
        discoveredAdapter?.notifyDataSetChanged()
    }

    @SuppressLint("MissingPermission")
    private fun rowForDevice(device: BluetoothDevice): String {
        val name = device.name?.ifBlank { "Niimbot" } ?: "Niimbot"
        val suffix = when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> " (emparejada)"
            BluetoothDevice.BOND_BONDING -> " (emparejando...)"
            else -> ""
        }
        val lastMac = niimbotPrefs.getString(KEY_LAST_NIIMBOT_MAC, null)
        val preferred = if (!lastMac.isNullOrBlank() && lastMac.equals(device.address, ignoreCase = true)) {
            " (ultima usada)"
        } else ""
        return "$name - ${device.address}$suffix$preferred"
    }

    @SuppressLint("MissingPermission")
    private fun connectAndPrint(device: BluetoothDevice, fallbackToScanOnFailure: Boolean = false) {
        showNiimbotPrintingDialog(
            message = "Conectando con ${device.name ?: "Niimbot"}",
            animationRes = R.raw.connect_print
        )
        lifecycleScope.launch(Dispatchers.IO) {
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                try { device.createBond() } catch (_: Exception) {}
                delay(1200)
            }

            var connectResult = -1
            var connected = false
            for (idx in 0 until 3) {
                connectResult = NiimbotSdkManager.connectBluetooth(requireContext(), device.address)
                if (connectResult == 0 || NiimbotSdkManager.isConnected(requireContext())) {
                    connected = true
                    break
                }
                if (idx < 2) delay(700)
            }
            if (!connected && connectResult != 0 && !NiimbotSdkManager.isConnected(requireContext())) {
                withContext(Dispatchers.Main) {
                    dismissNiimbotPrintingDialog()
                    CreateUiFeedback.showErrorPopup(
                        activity = requireActivity(),
                        title = "No se pudo conectar",
                        details = "No se pudo conectar con la impresora",
                        animationRes = R.raw.print_error
                    )
                    if (fallbackToScanOnFailure) {
                        showBluetoothScanDialog()
                        startBluetoothDiscovery()
                    }
                }
                return@launch
            }

            saveLastPrinter(device)
            val raw = withContext(Dispatchers.Main) { waitAndCaptureLabelBitmap() }
            if (raw == null) {
                withContext(Dispatchers.Main) {
                    dismissNiimbotPrintingDialog()
                    UiNotifier.show(requireActivity(), "No se pudo preparar la etiqueta")
                }
                return@launch
            }

            val trimmed = trimVerticalWhitespace(raw)
            val prepared = NiimbotSdkManager.prepareBitmapFor50x30(trimmed, marginMm = 2f)
            withContext(Dispatchers.Main) {
                setNiimbotPrintingAnimation(R.raw.printing)
                setNiimbotPrintingLabel("Imprimiendo etiqueta")
            }
            NiimbotSdkManager.printBitmap(
                context = requireContext(),
                bitmap = prepared,
                onProgress = { msg ->
                    setNiimbotPrintingLabel(msg)
                },
                onSuccess = {
                    dismissNiimbotPrintingDialog()
                    CreateUiFeedback.showStatusPopup(
                        activity = requireActivity(),
                        title = "Etiqueta impresa",
                        details = "Etiqueta impresa correctamente",
                        animationRes = R.raw.correct_create
                    )
                },
                onError = { msg ->
                    dismissNiimbotPrintingDialog()
                    UiNotifier.show(requireActivity(), msg)
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun saveLastPrinter(device: BluetoothDevice) {
        niimbotPrefs.edit()
            .putString(KEY_LAST_NIIMBOT_MAC, device.address)
            .putString(KEY_LAST_NIIMBOT_NAME, device.name ?: "Niimbot")
            .apply()
    }

    private suspend fun waitAndCaptureLabelBitmap(attempts: Int = 6): Bitmap? {
        repeat(attempts) {
            val bmp = capturePreviewBitmap()
            if (bmp != null) return bmp
            delay(140)
        }
        return null
    }

    private fun showNiimbotPrintingDialog(message: String, animationRes: Int = R.raw.printing) {
        val view = layoutInflater.inflate(R.layout.dialog_niimbot_printing, null)
        view.findViewById<android.widget.TextView>(R.id.tvNiimbotPrintingLabel).text = message
        applyNiimbotDialogAnimationStyle(
            view.findViewById(R.id.lottieNiimbotPrinting),
            animationRes
        )
        niimbotPrintDialog?.dismiss()
        niimbotPrintDialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
        niimbotPrintDialog?.show()
        niimbotPrintDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun setNiimbotPrintingAnimation(animationRes: Int) {
        val lottie = niimbotPrintDialog
            ?.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.lottieNiimbotPrinting)
        if (lottie != null) {
            applyNiimbotDialogAnimationStyle(lottie, animationRes)
        }
    }

    private fun applyNiimbotDialogAnimationStyle(
        lottie: com.airbnb.lottie.LottieAnimationView,
        animationRes: Int
    ) {
        lottie.setAnimation(animationRes)
        val lp = lottie.layoutParams
        when (animationRes) {
            R.raw.connect_print -> {
                lp.width = dpToPx(128)
                lp.height = dpToPx(110)
                lottie.layoutParams = lp
                lottie.scaleX = 1.22f
                lottie.scaleY = 1.22f
                lottie.translationY = -dpToPx(8).toFloat()
            }
            R.raw.printing -> {
                lp.width = dpToPx(124)
                lp.height = dpToPx(102)
                lottie.layoutParams = lp
                lottie.scaleX = 1.18f
                lottie.scaleY = 1.18f
                lottie.translationY = -dpToPx(3).toFloat()
            }
            else -> {
                lp.width = dpToPx(120)
                lp.height = dpToPx(120)
                lottie.layoutParams = lp
                lottie.scaleX = 1f
                lottie.scaleY = 1f
                lottie.translationY = 0f
            }
        }
        lottie.playAnimation()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setNiimbotPrintingLabel(message: String) {
        val tv = niimbotPrintDialog
            ?.findViewById<android.widget.TextView>(R.id.tvNiimbotPrintingLabel)
        tv?.text = message
    }

    private fun dismissNiimbotPrintingDialog() {
        niimbotPrintDialog?.dismiss()
        niimbotPrintDialog = null
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
                val resolver = requireContext().contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    UiNotifier.show(requireActivity(), "No se pudo crear el archivo en la galerÃƒÂ­a")
                    return false
                }
                val outStream = resolver.openOutputStream(uri)
                if (outStream == null) {
                    UiNotifier.show(requireActivity(), "No se pudo abrir el archivo en la galerÃƒÂ­a")
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
            UiNotifier.show(requireActivity(), "Error guardando etiqueta: ${e.message}")
            false
        }
    }

    private fun checkRoleForRegenerate() {
        binding.btnRegenerate.visibility = android.view.View.GONE
        binding.btnPrint.visibility = android.view.View.GONE
        binding.btnSaveNiimbot.visibility = android.view.View.GONE
        binding.ivPrintLock.visibility = android.view.View.GONE
        binding.ivNiimbotLock.visibility = android.view.View.GONE
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.me()
                if (res.isSuccessful && res.body() != null) {
                    val role = res.body()!!.role.uppercase()
                    applyRegenerateVisibility(role)
                    applyPrintVisibility(role)
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

        val prefs = requireContext().getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val showRestricted = prefs.getBoolean("show_restricted_cards", false)
        if (!showRestricted) {
            binding.btnRegenerate.visibility = android.view.View.GONE
            return
        }

        binding.btnRegenerate.visibility = android.view.View.VISIBLE
        binding.btnRegenerate.isEnabled = false
        binding.btnRegenerate.alpha = 0.6f
        binding.btnRegenerate.text = "Bloqueado"
        binding.btnRegenerate.setCompoundDrawablesWithIntrinsicBounds(
            com.example.inventoryapp.R.drawable.ic_lock,
            0,
            0,
            0
        )
        binding.btnRegenerate.compoundDrawablePadding = 12
    }

    private fun applyPrintVisibility(role: String) {
        if (role == "MANAGER" || role == "ADMIN") {
            binding.btnPrint.visibility = android.view.View.VISIBLE
            binding.btnSaveNiimbot.visibility = android.view.View.VISIBLE
            binding.ivPrintLock.visibility = android.view.View.GONE
            binding.ivNiimbotLock.visibility = android.view.View.GONE
            binding.btnPrint.isEnabled = true
            binding.btnSaveNiimbot.isEnabled = true
            binding.btnPrint.alpha = 1.0f
            binding.btnSaveNiimbot.alpha = 1.0f
            return
        }

        val prefs = requireContext().getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val showRestricted = prefs.getBoolean("show_restricted_cards", false)
        if (!showRestricted) {
            binding.btnPrint.visibility = android.view.View.GONE
            binding.btnSaveNiimbot.visibility = android.view.View.GONE
            binding.ivPrintLock.visibility = android.view.View.GONE
            binding.ivNiimbotLock.visibility = android.view.View.GONE
            return
        }

        binding.btnPrint.visibility = android.view.View.VISIBLE
        binding.btnSaveNiimbot.visibility = android.view.View.VISIBLE
        binding.btnPrint.isEnabled = false
        binding.btnSaveNiimbot.isEnabled = false
        binding.btnPrint.alpha = 0.6f
        binding.btnSaveNiimbot.alpha = 0.6f
        binding.ivPrintLock.visibility = android.view.View.VISIBLE
        binding.ivNiimbotLock.visibility = android.view.View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT_PERMS) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                startDirectNiimbotPrintFlow()
            } else {
                CreateUiFeedback.showErrorPopup(
                    activity = requireActivity(),
                    title = "Permisos Bluetooth requeridos",
                    details = "Permisos Bluetooth requeridos para imprimir",
                    animationRes = R.raw.error
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBluetoothDiscovery()
        saveLabelLoadingHandle?.dismiss()
        saveLabelLoadingHandle = null
        dismissNiimbotPrintingDialog()
        niimbotScanDialog?.dismiss()
        niimbotScanDialog = null
        runCatching {
            binding.webLabel.stopLoading()
            binding.webLabel.loadUrl("about:blank")
            binding.webLabel.clearHistory()
            binding.webLabel.removeAllViews()
            binding.webLabel.destroy()
        }
        _binding = null
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        val host = activity as? ProductListActivity ?: return
        if (!host.isFinishing && !host.isDestroyed) {
            host.recreate()
        }
    }


    private fun saveToDownloads(filename: String, mime: String, bytes: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = requireContext().contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(requireContext().cacheDir, "downloads")
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







