package com.example.inventoryapp.ui.audit

import android.app.DatePickerDialog
import android.app.Dialog
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.AuditLogResponseDto
import com.example.inventoryapp.databinding.ActivityAuditBinding
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.UiNotifier
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import kotlin.math.absoluteValue

class AuditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuditBinding
    private lateinit var adapter: AuditAdapter

    private val pageSize = 20
    private var currentPage = 0
    private var currentTotal = 0
    private var currentItemsCount = 0
    private var csvSeparator = ','

    private enum class RequestSource {
        INITIAL,
        APPLY,
        CLEAR,
        REFRESH,
        PAGE
    }

    private enum class ExportFormat {
        JSON,
        CSV,
        TEXT
    }

    private data class AuditFilters(
        val entity: String?,
        val action: String?,
        val userIdRaw: String,
        val userId: Int?,
        val dateFrom: String?,
        val dateTo: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))
        applyTitleGradient()

        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        if (!role.equals("ADMIN", ignoreCase = true)) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "Solo un administrador puede consultar la auditoria.",
                R.drawable.ic_lock
            )
            finish()
            return
        }

        GradientIconUtil.applyGradient(binding.ivSearchAudit, R.drawable.search)
        GradientIconUtil.applyGradient(binding.ivExportAudit, R.drawable.export)
        binding.btnBack.setOnClickListener { finish() }

        setupRecycler()
        setupFilters()
        setupDatePickers()
        setupActions()
        applyPagerButtonStyle(binding.btnPrevAudit, enabled = false)
        applyPagerButtonStyle(binding.btnNextAudit, enabled = false)

        loadAudit(resetPage = true, source = RequestSource.INITIAL)
    }

    private fun setupRecycler() {
        adapter = AuditAdapter { item -> showAuditDetailDialog(item) }
        binding.rvAudit.layoutManager = LinearLayoutManager(this)
        binding.rvAudit.adapter = adapter
    }

    private fun setupFilters() {
        val entities = listOf(
            "",
            "PRODUCT",
            "STOCK",
            "MOVEMENT",
            "IMPORT",
            "CATEGORY",
            "EVENT",
            "ALERT",
            "STOCK_THRESHOLD",
            "USER"
        )
        val actions = listOf("", "CREATE", "UPDATE", "DELETE")

        binding.etEntity.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, entities))
        binding.etAction.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, actions))

        binding.etEntity.setOnClickListener { binding.etEntity.showDropDown() }
        binding.etAction.setOnClickListener { binding.etAction.showDropDown() }
        disableKeyboardForDropdown(binding.etEntity)
        disableKeyboardForDropdown(binding.etAction)

        applyDropdownIconGradient()
    }

    private fun applyDropdownIconGradient() {
        binding.tilEntity.setEndIconTintList(null)
        binding.tilAction.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilEntity.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
        binding.tilAction.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
    }

    private fun setupDatePickers() {
        bindDateField(binding.etDateFrom)
        bindDateField(binding.etDateTo)
    }

    private fun bindDateField(field: android.widget.EditText) {
        field.setOnClickListener { showDatePicker(field) }
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDatePicker(field)
        }
    }

    private fun showDatePicker(target: android.widget.EditText) {
        val cal = Calendar.getInstance()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val mm = String.format("%02d", month + 1)
                val dd = String.format("%02d", dayOfMonth)
                target.setText("$year-$mm-$dd")
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setButton(
            DatePickerDialog.BUTTON_NEUTRAL,
            "Hoy"
        ) { _, _ ->
            target.setText(todayIsoDate())
        }
        dialog.show()
    }

    private fun setupActions() {
        binding.layoutSearchAuditHeader.setOnClickListener { toggleSearchForm() }
        binding.layoutExportAuditHeader.setOnClickListener { toggleExportForm() }
        binding.btnExportAuditJson.setOnClickListener { exportFilteredAudit(ExportFormat.JSON) }
        binding.btnExportAuditCsv.setOnClickListener { showCsvSeparatorPickerAndExport() }
        binding.btnExportAuditText.setOnClickListener { exportFilteredAudit(ExportFormat.TEXT) }

        binding.btnApplyFilters.setOnClickListener {
            hideKeyboard()
            loadAudit(resetPage = true, source = RequestSource.APPLY)
        }

        binding.btnClearFilters.setOnClickListener {
            hideKeyboard()
            binding.etEntity.setText("")
            binding.etAction.setText("")
            binding.etUserId.setText("")
            binding.etDateFrom.setText("")
            binding.etDateTo.setText("")
            loadAudit(resetPage = true, source = RequestSource.CLEAR)
        }

        binding.btnRefreshAudit.setOnClickListener {
            loadAudit(resetPage = false, source = RequestSource.REFRESH)
        }

        binding.btnPrevAudit.setOnClickListener {
            if (currentPage <= 0) return@setOnClickListener
            currentPage -= 1
            loadAudit(resetPage = false, source = RequestSource.PAGE)
            binding.rvAudit.scrollToPosition(0)
        }

        binding.btnNextAudit.setOnClickListener {
            val nextStart = (currentPage + 1) * pageSize
            if (nextStart >= currentTotal) return@setOnClickListener
            currentPage += 1
            loadAudit(resetPage = false, source = RequestSource.PAGE)
            binding.rvAudit.scrollToPosition(0)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollAudit, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchAuditContent.isVisible
        binding.layoutSearchAuditContent.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.layoutSearchAuditHeader.setBackgroundResource(
            if (isVisible) R.drawable.bg_toggle_idle else R.drawable.bg_toggle_active
        )
    }

    private fun toggleExportForm() {
        TransitionManager.beginDelayedTransition(binding.scrollAudit, AutoTransition().setDuration(180))
        val isVisible = binding.layoutExportAuditContent.isVisible
        binding.layoutExportAuditContent.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.layoutExportAuditHeader.setBackgroundResource(
            if (isVisible) R.drawable.bg_toggle_idle else R.drawable.bg_toggle_active
        )
    }

    private fun loadAudit(resetPage: Boolean, source: RequestSource) {
        if (resetPage) currentPage = 0

        val filters = readFiltersOrShowError() ?: return
        val offset = currentPage * pageSize
        val showReloadDialogs = source == RequestSource.REFRESH
        val loadingHandle = if (showReloadDialogs) {
            CreateUiFeedback.showListLoading(this, "Cargando auditorias", minCycles = 2)
        } else {
            null
        }

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listAuditLogs(
                    entity = filters.entity,
                    action = filters.action,
                    userId = filters.userId,
                    dateFrom = filters.dateFrom,
                    dateTo = filters.dateTo,
                    orderDir = "desc",
                    limit = pageSize,
                    offset = offset
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    currentTotal = body.total
                    currentItemsCount = body.items.size
                    adapter.submit(body.items)
                    binding.tvAuditEmpty.visibility = if (body.items.isEmpty()) View.VISIBLE else View.GONE
                    updateCounter(offset)
                    val prevEnabled = currentPage > 0
                    val nextEnabled = offset + currentItemsCount < currentTotal
                    binding.btnPrevAudit.isEnabled = prevEnabled
                    binding.btnNextAudit.isEnabled = nextEnabled
                    applyPagerButtonStyle(binding.btnPrevAudit, prevEnabled)
                    applyPagerButtonStyle(binding.btnNextAudit, nextEnabled)
                    if (body.items.isEmpty() && (source == RequestSource.APPLY || source == RequestSource.REFRESH)) {
                        CreateUiFeedback.showErrorPopup(
                            this@AuditActivity,
                            "Sin resultados",
                            buildNoResultsMessage(filters.entity, filters.action, filters.userIdRaw, filters.dateFrom, filters.dateTo)
                        )
                    } else if (showReloadDialogs) {
                        loadingHandle?.dismissThen {
                            CreateUiFeedback.showCreatedPopup(
                                this@AuditActivity,
                                "Auditorias cargadas",
                                "Se actualizaron los registros."
                            )
                        }
                        return@launch
                    }
                } else {
                    CreateUiFeedback.showErrorPopup(
                        this@AuditActivity,
                        "No se pudo cargar auditoria",
                        ApiErrorFormatter.format(res.code())
                    )
                }
            } catch (e: Exception) {
                CreateUiFeedback.showErrorPopup(
                    this@AuditActivity,
                    "Error de conexion",
                    e.message ?: "Fallo inesperado al cargar auditoria."
                )
            } finally {
                loadingHandle?.dismiss()
            }
        }
    }

    private fun readFiltersOrShowError(): AuditFilters? {
        val entity = binding.etEntity.text?.toString()?.trim().orEmpty().ifBlank { null }
        val action = binding.etAction.text?.toString()?.trim().orEmpty().ifBlank { null }
        val userIdRaw = binding.etUserId.text?.toString()?.trim().orEmpty()
        if (userIdRaw.isNotBlank() && userIdRaw.toIntOrNull() == null) {
            CreateUiFeedback.showErrorPopup(
                this,
                "Busqueda invalida",
                "El User ID debe ser numerico."
            )
            return null
        }
        val userId = userIdRaw.toIntOrNull()
        val dateFrom = normalizeDateStart(binding.etDateFrom.text?.toString()?.trim())
        val dateTo = normalizeDateEnd(binding.etDateTo.text?.toString()?.trim())
        return AuditFilters(
            entity = entity,
            action = action,
            userIdRaw = userIdRaw,
            userId = userId,
            dateFrom = dateFrom,
            dateTo = dateTo
        )
    }

    private fun updateCounter(offset: Int) {
        if (currentTotal == 0 || currentItemsCount == 0) {
            binding.tvAuditCounter.text = "Mostrando 0/0"
            return
        }
        val shown = offset + currentItemsCount
        binding.tvAuditCounter.text = "Mostrando $shown/$currentTotal"
    }

    private fun showAuditDetailDialog(item: AuditLogResponseDto) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_audit_detail, null)
        view.findViewById<TextView>(R.id.tvAuditDialogTitle)?.text = "Registro de auditoria"
        view.findViewById<TextView>(R.id.tvAuditDialogMessage)?.text = buildAuditDetailText(item)

        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<Button>(R.id.btnAuditDialogClose)?.setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnAuditDialogExportJson)?.setOnClickListener {
            exportSingleAuditEntry(item, asJson = true)
        }
        view.findViewById<Button>(R.id.btnAuditDialogExportTxt)?.setOnClickListener {
            exportSingleAuditEntry(item, asJson = false)
        }
    }

    private fun normalizeDateStart(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return if (raw.length == 10) "${raw}T00:00:00" else raw
    }

    private fun normalizeDateEnd(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return if (raw.length == 10) "${raw}T23:59:59" else raw
    }

    private fun applyPagerButtonStyle(button: Button, enabled: Boolean) {
        button.backgroundTintList = null
        if (!enabled) {
            val colors = intArrayOf(
                ContextCompat.getColor(this, android.R.color.darker_gray),
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
            val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
                cornerRadius = resources.displayMetrics.density * 18f
                setStroke((resources.displayMetrics.density * 1f).toInt(), 0xFFB0B0B0.toInt())
            }
            button.background = drawable
            button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            return
        }
        val colors = intArrayOf(
            ContextCompat.getColor(this, R.color.icon_grad_start),
            ContextCompat.getColor(this, R.color.icon_grad_mid2),
            ContextCompat.getColor(this, R.color.icon_grad_mid1),
            ContextCompat.getColor(this, R.color.icon_grad_end)
        )
        val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = resources.displayMetrics.density * 18f
            setStroke((resources.displayMetrics.density * 1f).toInt(), 0x33000000)
        }
        button.background = drawable
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun applyTitleGradient() {
        binding.tvAuditTitle.post {
            val paint = binding.tvAuditTitle.paint
            val width = paint.measureText(binding.tvAuditTitle.text.toString())
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
            binding.tvAuditTitle.invalidate()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun disableKeyboardForDropdown(field: EditText) {
        field.keyListener = null
        field.showSoftInputOnFocus = false
        field.isCursorVisible = false
    }

    private fun todayIsoDate(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val mm = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val dd = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
        return "$year-$mm-$dd"
    }

    private fun buildNoResultsMessage(
        entity: String?,
        action: String?,
        userId: String?,
        dateFrom: String?,
        dateTo: String?
    ): String {
        val filters = mutableListOf<String>()
        entity?.let { filters.add("entidad=$it") }
        action?.let { filters.add("accion=$it") }
        if (!userId.isNullOrBlank()) filters.add("userId=$userId")
        dateFrom?.let { filters.add("desde=${it.take(10)}") }
        dateTo?.let { filters.add("hasta=${it.take(10)}") }
        return if (filters.isEmpty()) {
            "No hay registros de auditoria para mostrar."
        } else {
            "No se encontraron registros para: ${filters.joinToString(", ")}."
        }
    }

    private fun buildAuditDetailText(item: AuditLogResponseDto): CharSequence {
        val header = "${item.action} - ${item.entity}"
        return buildString {
            append(header)
            append("\n\n")
            append("ID usuario: ${item.user_id}\n")
            append("Fecha: ${item.created_at}\n")
            append("Comentario: ${item.details ?: "Sin comentario"}")
        }.let { raw ->
            val spannable = android.text.SpannableString(raw)
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0,
                header.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable
        }
    }

    private fun exportSingleAuditEntry(item: AuditLogResponseDto, asJson: Boolean) {
        val payload = if (asJson) {
            """
            {
              "id": ${item.id},
              "action": "${escapeJson(item.action)}",
              "entity": "${escapeJson(item.entity)}",
              "user_id": ${item.user_id},
              "created_at": "${escapeJson(item.created_at)}",
              "details": "${escapeJson(item.details ?: "")}"
            }
            """.trimIndent()
        } else {
            buildString {
                append("AUDITORIA\n")
                append("id: ${item.id}\n")
                append("action: ${item.action}\n")
                append("entity: ${item.entity}\n")
                append("user_id: ${item.user_id}\n")
                append("created_at: ${item.created_at}\n")
                append("details: ${item.details ?: ""}\n")
            }
        }

        val mime = if (asJson) "application/json" else "text/plain"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_SUBJECT, "auditoria_${item.id}")
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        startActivity(Intent.createChooser(shareIntent, "Exportar auditoria"))
    }

    private fun exportFilteredAudit(format: ExportFormat) {
        val filters = readFiltersOrShowError() ?: return
        val loading = CreateUiFeedback.showListLoading(this, "Exportando auditorias", minCycles = 2)
        lifecycleScope.launch {
            try {
                val all = fetchAllFilteredAudit(filters)
                if (all.isEmpty()) {
                    loading.dismissThen {
                        CreateUiFeedback.showErrorPopup(
                            this@AuditActivity,
                            "Sin resultados",
                            buildNoResultsMessage(filters.entity, filters.action, filters.userIdRaw, filters.dateFrom, filters.dateTo)
                        )
                    }
                    return@launch
                }
                val exportResult = buildExportFile(all, format)
                val savedUri = saveToDownloads(exportResult.filename, exportResult.mime, exportResult.bytes)
                loading.dismissThen {
                    if (savedUri != null) {
                        CreateUiFeedback.showCreatedPopup(
                            this@AuditActivity,
                            "Exportacion completada",
                            "Archivo guardado: ${exportResult.filename}"
                        )
                        showExportedNotification(exportResult.filename, savedUri)
                    } else {
                        CreateUiFeedback.showErrorPopup(
                            this@AuditActivity,
                            "Exportacion fallida",
                            "No se pudo guardar el archivo."
                        )
                    }
                }
            } catch (e: Exception) {
                loading.dismissThen {
                    CreateUiFeedback.showErrorPopup(
                        this@AuditActivity,
                        "Exportacion fallida",
                        e.message ?: "No se pudieron exportar las auditorias."
                    )
                }
            }
        }
    }

    private suspend fun fetchAllFilteredAudit(filters: AuditFilters): List<AuditLogResponseDto> {
        val collected = mutableListOf<AuditLogResponseDto>()
        var offset = 0
        val chunk = 200
        var total = Int.MAX_VALUE
        while (offset < total) {
            val res = NetworkModule.api.listAuditLogs(
                entity = filters.entity,
                action = filters.action,
                userId = filters.userId,
                dateFrom = filters.dateFrom,
                dateTo = filters.dateTo,
                orderDir = "desc",
                limit = chunk,
                offset = offset
            )
            if (!res.isSuccessful || res.body() == null) {
                throw IllegalStateException(ApiErrorFormatter.format(res.code()))
            }
            val body = res.body()!!
            total = body.total
            if (body.items.isEmpty()) break
            collected.addAll(body.items)
            offset += body.items.size
            if (body.items.size < chunk) break
        }
        return collected
    }

    private fun buildExportFile(
        items: List<AuditLogResponseDto>,
        format: ExportFormat
    ): ExportResult {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(java.util.Date())
        return when (format) {
            ExportFormat.JSON -> {
                val content = buildJson(items)
                ExportResult("audit_$stamp.json", "application/json", content.toByteArray(Charsets.UTF_8))
            }
            ExportFormat.CSV -> {
                val content = buildCsv(items)
                ExportResult("audit_$stamp.csv", "text/csv", content.toByteArray(Charsets.UTF_8))
            }
            ExportFormat.TEXT -> {
                val content = buildText(items)
                ExportResult("audit_$stamp.txt", "text/plain", content.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun buildJson(items: List<AuditLogResponseDto>): String {
        return buildString {
            append("[\n")
            items.forEachIndexed { index, item ->
                append("  {\n")
                append("    \"id\": ${item.id},\n")
                append("    \"action\": \"${escapeJson(item.action)}\",\n")
                append("    \"entity\": \"${escapeJson(item.entity)}\",\n")
                append("    \"user_id\": ${item.user_id},\n")
                append("    \"created_at\": \"${escapeJson(item.created_at)}\",\n")
                append("    \"details\": \"${escapeJson(item.details ?: "")}\"\n")
                append("  }")
                if (index < items.lastIndex) append(",")
                append("\n")
            }
            append("]")
        }
    }

    private fun buildCsv(items: List<AuditLogResponseDto>): String {
        val sep = csvSeparator
        return buildString {
            append("id").append(sep)
                .append("action").append(sep)
                .append("entity").append(sep)
                .append("user_id").append(sep)
                .append("created_at").append(sep)
                .append("details\n")
            items.forEach { item ->
                append(item.id).append(sep)
                append(csv(item.action)).append(sep)
                append(csv(item.entity)).append(sep)
                append(item.user_id).append(sep)
                append(csv(item.created_at)).append(sep)
                append(csv(item.details ?: "")).append('\n')
            }
        }
    }

    private fun buildText(items: List<AuditLogResponseDto>): String {
        val block = "=".repeat(56)
        val sep = "-".repeat(56)
        return buildString {
            items.forEachIndexed { index, item ->
                append(block).append('\n')
                append("REGISTRO ${index + 1}\n")
                append(sep).append('\n')
                append("id: ${item.id}\n")
                append("action: ${item.action}\n")
                append("entity: ${item.entity}\n")
                append("user_id: ${item.user_id}\n")
                append("created_at: ${item.created_at}\n")
                append("details: ${item.details ?: ""}\n")
                append(block).append('\n')
                append('\n')
            }
        }
    }

    private fun csv(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun showCsvSeparatorPickerAndExport() {
        val labels = arrayOf("Coma (,)", "Punto y coma (;)", "Tabulador (\\t)", "Pipe (|)")
        val values = charArrayOf(',', ';', '\t', '|')
        val selected = values.indexOf(csvSeparator).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Separador CSV")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                csvSeparator = values[which]
                dialog.dismiss()
                exportFilteredAudit(ExportFormat.CSV)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showExportedNotification(filename: String, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val channelId = "audit_exports"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    android.app.NotificationChannel(
                        channelId,
                        "Exportaciones de auditoria",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
        }

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = android.app.PendingIntent.getActivity(
            this,
            filename.hashCode().absoluteValue,
            Intent.createChooser(openIntent, "Abrir archivo exportado"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.system)
            .setContentTitle("Exportacion completada")
            .setContentText(filename)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Archivo guardado: $filename"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(filename.hashCode().absoluteValue, notification)
    }

    private data class ExportResult(
        val filename: String,
        val mime: String,
        val bytes: ByteArray
    )

    private fun saveToDownloads(filename: String, mime: String, bytes: ByteArray): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(cacheDir, "downloads")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { it.write(bytes) }
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeJson(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
