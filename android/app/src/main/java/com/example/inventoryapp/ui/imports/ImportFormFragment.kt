package com.example.inventoryapp.ui.imports

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.model.ImportSummaryResponseDto
import com.example.inventoryapp.databinding.FragmentImportFormBinding
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

abstract class ImportFormFragment : Fragment(R.layout.fragment_import_form) {

    protected abstract val titleLabel: String
    protected abstract val sendLabel: String
    protected abstract val errorListIconRes: Int
    protected abstract val cacheKeyPrefix: String
    protected abstract suspend fun uploadCsv(
        filePart: MultipartBody.Part,
        dryRun: Boolean,
        fuzzyThreshold: Double
    ): ImportSummaryResponseDto?

    private var _binding: FragmentImportFormBinding? = null
    protected val binding get() = _binding!!

    private var selectedUri: Uri? = null
    private var selectedName: String? = null
    private lateinit var cacheStore: CacheStore
    private val allErrorRows = mutableListOf<ImportErrorRow>()
    private val pageSize = 5
    private var currentOffset = 0
    private var summaryStatsText: String = "Total: 0 | OK: 0 | Errores: 0 | Reviews: 0"
    private var importDialog: AlertDialog? = null

    data class ImportUiCacheDto(
        val rows: List<ImportErrorRow>,
        val summaryStats: String
    )

    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ignore if persistable permission is not granted.
            }
            selectedUri = uri
            selectedName = guessFileName(uri) ?: "archivo.csv"
            updateDialogSelectedFile()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentImportFormBinding.bind(view)
        cacheStore = CacheStore.getInstance(requireContext())

        val errorAdapter = ImportErrorAdapter(emptyList())
        binding.rvErrors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvErrors.adapter = errorAdapter

        binding.tvSummaryTitle.text = "Importacion completada"
        binding.tvSummaryStats.text = summaryStatsText
        binding.btnRefreshErrors.setOnClickListener { loadCachedErrors(errorAdapter) }
        binding.btnClearLocalImportCache.setOnClickListener { clearLocalCache(errorAdapter) }

        setupPagination(errorAdapter)
        loadCachedErrors(errorAdapter)
        applyRefreshIconTint()
        applyPagerButtonStyle(binding.btnPrevErrors, enabled = false)
        applyPagerButtonStyle(binding.btnNextErrors, enabled = false)
    }

    fun openImportDialog() {
        if (!isAdded) return
        if (importDialog?.isShowing == true) return

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_import_csv_master, null)
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvDialogImportTitle)
        val btnClose = view.findViewById<android.widget.ImageButton>(R.id.btnDialogImportClose)
        val btnSelect = view.findViewById<android.widget.Button>(R.id.btnDialogSelectFile)
        val tvSelected = view.findViewById<android.widget.TextView>(R.id.tvDialogSelectedFile)
        val switchDryRun = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchDialogDryRun)
        val tvFuzzy = view.findViewById<android.widget.TextView>(R.id.tvDialogFuzzyLabel)
        val slider = view.findViewById<Slider>(R.id.sliderDialogFuzzy)
        val btnSend = view.findViewById<android.widget.Button>(R.id.btnDialogSendCsv)
        val progress = view.findViewById<android.widget.ProgressBar>(R.id.progressDialogImport)

        tvTitle.text = titleLabel
        btnSend.text = sendLabel
        tvSelected.text = selectedName ?: "Ningun archivo seleccionado"

        slider.stepSize = 0.1f
        slider.valueFrom = 0f
        slider.valueTo = 1f
        slider.value = 0.9f
        tvFuzzy.text = buildFuzzyLabel(slider.value)

        slider.addOnChangeListener { _, value, _ ->
            val rounded = ((value * 10f).toInt() / 10f)
            tvFuzzy.text = buildFuzzyLabel(rounded)
        }

        val dialog = AlertDialog.Builder(requireContext()).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnSelect.setOnClickListener {
            pickCsv.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/*"))
        }
        btnSend.setOnClickListener {
            val uri = selectedUri
            if (uri == null) {
                CreateUiFeedback.showStatusPopup(
                    activity = requireActivity(),
                    title = "Archivo requerido",
                    details = "Selecciona un CSV primero",
                    animationRes = R.raw.file
                )
                return@setOnClickListener
            }

            val fuzzy = ((slider.value * 10f).toInt() / 10.0)
            val dryRun = switchDryRun.isChecked
            btnSend.isEnabled = false
            progress.visibility = View.VISIBLE
            upload(uri, selectedName ?: "import.csv", dryRun, fuzzy, btnSend, progress)
            dialog.dismiss()
        }

        dialog.setOnDismissListener { importDialog = null }
        importDialog = dialog
        dialog.show()
    }

    private fun buildFuzzyLabel(value: Float): String {
        return String.format(Locale.US, "Umbral de similitud (Fuzzy threshold): %.1f", value)
    }

    private fun updateDialogSelectedFile() {
        val dialogView = importDialog?.findViewById<android.widget.TextView>(R.id.tvDialogSelectedFile) ?: return
        dialogView.text = selectedName ?: "Ningun archivo seleccionado"
    }

    private fun setupPagination(errorAdapter: ImportErrorAdapter) {
        binding.btnPrevErrors.setOnClickListener {
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            renderPage(errorAdapter)
        }
        binding.btnNextErrors.setOnClickListener {
            if (currentOffset + pageSize >= allErrorRows.size) return@setOnClickListener
            currentOffset += pageSize
            renderPage(errorAdapter)
        }
        renderPage(errorAdapter)
    }

    private fun loadCachedErrors(errorAdapter: ImportErrorAdapter) {
        lifecycleScope.launch {
            val key = "$cacheKeyPrefix:import_ui_state"
            val cached = cacheStore.get(key, ImportUiCacheDto::class.java)
            if (cached != null) {
                allErrorRows.clear()
                allErrorRows.addAll(cached.rows)
                summaryStatsText = cached.summaryStats
                binding.tvSummaryStats.text = summaryStatsText
                if (allErrorRows.isNotEmpty()) {
                    currentOffset = ((allErrorRows.size - 1) / pageSize) * pageSize
                }
                renderPage(errorAdapter)
            } else {
                binding.tvSummaryStats.text = summaryStatsText
                renderPage(errorAdapter)
            }
        }
    }

    private fun upload(
        uri: Uri,
        fileName: String,
        dryRun: Boolean,
        fuzzy: Double,
        btnSend: android.widget.Button,
        progress: android.widget.ProgressBar
    ) {
        lifecycleScope.launch {
            try {
                val bytes = requireContext().contentResolver.openInputStream(uri)?.readBytes()
                if (bytes == null || bytes.isEmpty()) {
                    CreateUiFeedback.showErrorPopup(
                        activity = requireActivity(),
                        title = "CSV vacio",
                        details = "El archivo CSV esta vacio",
                        animationRes = R.raw.notfound
                    )
                    return@launch
                }
                val requestBody = bytes.toRequestBody("text/csv".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
                val res = uploadCsv(part, dryRun, fuzzy)
                if (res != null) {
                    showSummary(res)
                } else {
                    CreateUiFeedback.showErrorPopup(
                        activity = requireActivity(),
                        title = "Error de importacion",
                        details = "Respuesta vacia del servidor",
                        animationRes = R.raw.error
                    )
                }
            } catch (e: Exception) {
                showImportError(e.message ?: "Error de importacion")
            } finally {
                progress.visibility = View.GONE
                btnSend.isEnabled = true
            }
        }
    }

    private fun showImportError(details: String) {
        val detailLower = details.lowercase()
        val isOfflineError = detailLower.contains("offline") ||
            detailLower.contains("backend unavailable") ||
            detailLower.contains("manual offline mode") ||
            detailLower.contains("sin conexión") ||
            detailLower.contains("sin conexion")
        if (isOfflineError) {
            CreateUiFeedback.showErrorPopup(
                activity = requireActivity(),
                title = "Modo offline",
                details = "No se puede importar CSV sin conexion. Activa la red y vuelve a intentarlo.",
                animationRes = R.raw.error
            )
            return
        }
        val isNotFound = detailLower.contains("no encontrado")
        CreateUiFeedback.showErrorPopup(
            activity = requireActivity(),
            title = if (isNotFound) "No encontrado" else "Error de importacion",
            details = details,
            animationRes = if (isNotFound) R.raw.notfound else R.raw.error
        )
    }

    private fun showSummary(res: ImportSummaryResponseDto) {
        summaryStatsText = "Total: ${res.total_rows} | OK: ${res.ok_rows} | Errores: ${res.error_rows} | Reviews: ${res.review_rows}"
        binding.tvSummaryStats.text = summaryStatsText

        if (res.errors.isNotEmpty()) {
            val appended = res.errors.map {
                ImportErrorRow(
                    rowNumber = it.row_number,
                    errorCode = it.error_code,
                    message = it.message,
                    batchId = res.batch_id,
                    iconRes = errorListIconRes
                )
            }
            allErrorRows.addAll(appended)
            currentOffset = ((allErrorRows.size - 1) / pageSize) * pageSize
        }
        renderPage(binding.rvErrors.adapter as ImportErrorAdapter)
        saveUiCache()

        if (res.dry_run) {
            CreateUiFeedback.showStatusPopup(
                activity = requireActivity(),
                title = "Dry-run completado",
                details = "Validacion terminada sin guardar cambios",
                animationRes = R.raw.correct_create
            )
        } else {
            CreateUiFeedback.showStatusPopup(
                activity = requireActivity(),
                title = "Importacion completada",
                details = "Importacion completada",
                animationRes = R.raw.correct_create
            )
        }
    }

    private fun renderPage(errorAdapter: ImportErrorAdapter) {
        if (_binding == null) return
        val total = allErrorRows.size
        if (total == 0) {
            binding.tvErrorsEmpty.visibility = View.VISIBLE
            binding.tvErrorsPageNumber.text = "Pagina 0/0"
            binding.tvErrorsPageInfo.text = "Mostrando 0/0"
            binding.btnPrevErrors.isEnabled = false
            binding.btnNextErrors.isEnabled = false
            applyPagerButtonStyle(binding.btnPrevErrors, enabled = false)
            applyPagerButtonStyle(binding.btnNextErrors, enabled = false)
            errorAdapter.submit(emptyList())
            return
        }

        binding.tvErrorsEmpty.visibility = View.GONE
        val from = currentOffset.coerceAtMost((total - 1).coerceAtLeast(0))
        val to = (from + pageSize).coerceAtMost(total)
        val currentPage = (from / pageSize) + 1
        val totalPages = (total + pageSize - 1) / pageSize
        val pageItems = allErrorRows.subList(from, to).toList()
        errorAdapter.submit(pageItems)

        binding.tvErrorsPageNumber.text = "Pagina $currentPage/$totalPages"
        binding.tvErrorsPageInfo.text = "Mostrando $to/$total"
        val prevEnabled = from > 0
        val nextEnabled = to < total
        binding.btnPrevErrors.isEnabled = prevEnabled
        binding.btnNextErrors.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevErrors, enabled = prevEnabled)
        applyPagerButtonStyle(binding.btnNextErrors, enabled = nextEnabled)
    }

    private fun saveUiCache() {
        lifecycleScope.launch {
            val key = "$cacheKeyPrefix:import_ui_state"
            cacheStore.put(
                key,
                ImportUiCacheDto(
                    rows = allErrorRows,
                    summaryStats = summaryStatsText
                )
            )
        }
    }

    private fun clearLocalCache(errorAdapter: ImportErrorAdapter) {
        lifecycleScope.launch {
            allErrorRows.clear()
            currentOffset = 0
            summaryStatsText = "Total: 0 | OK: 0 | Errores: 0 | Reviews: 0"
            binding.tvSummaryStats.text = summaryStatsText
            val key = "$cacheKeyPrefix:import_ui_state"
            cacheStore.invalidatePrefix(key)
            renderPage(errorAdapter)
            CreateUiFeedback.showStatusPopup(
                activity = requireActivity(),
                title = "Cache local limpiada",
                details = "Se ha limpiado el historial local de importacion.",
                animationRes = R.raw.correct_create
            )
        }
    }

    private fun applyPagerButtonStyle(button: android.widget.Button, enabled: Boolean) {
        button.backgroundTintList = null
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (!enabled) {
            val colors = intArrayOf(
                if (isDark) 0x334F6480 else 0x33A7BED8,
                if (isDark) 0x33445A74 else 0x338FA9C6
            )
            val drawable = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
                cornerRadius = resources.displayMetrics.density * 16f
                setStroke((resources.displayMetrics.density * 1f).toInt(), if (isDark) 0x44AFCBEB else 0x5597BCD9)
            }
            button.background = drawable
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.liquid_popup_hint))
            return
        }
        val colors = intArrayOf(
            if (isDark) 0x66789BC4 else 0x99D6EBFA.toInt(),
            if (isDark) 0x666D8DB4 else 0x99C5E0F4.toInt()
        )
        val drawable = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = resources.displayMetrics.density * 16f
            setStroke((resources.displayMetrics.density * 1f).toInt(), if (isDark) 0x88B5D5F4.toInt() else 0x88A7CBE6.toInt())
        }
        button.background = drawable
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.liquid_popup_button_text))
    }

    private fun applyRefreshIconTint() {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (!isDark) {
            val blue = ContextCompat.getColor(requireContext(), R.color.icon_grad_mid2)
            binding.btnRefreshErrors.setColorFilter(blue)
            binding.btnClearLocalImportCache.setColorFilter(blue)
        } else {
            binding.btnRefreshErrors.clearColorFilter()
            binding.btnClearLocalImportCache.clearColorFilter()
        }
    }

    private fun guessFileName(uri: Uri): String? {
        val nameCol = android.provider.OpenableColumns.DISPLAY_NAME
        requireContext().contentResolver.query(uri, arrayOf(nameCol), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(nameCol)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        importDialog?.dismiss()
        importDialog = null
        _binding = null
    }
}

