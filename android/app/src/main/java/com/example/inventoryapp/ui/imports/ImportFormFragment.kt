package com.example.inventoryapp.ui.imports

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.model.ImportSummaryResponseDto
import com.example.inventoryapp.databinding.FragmentImportFormBinding
import com.example.inventoryapp.ui.common.CreateUiFeedback
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

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
    private var importExpanded = true

    data class ImportUiCacheDto(
        val rows: List<ImportErrorRow>,
        val summary: String
    )

    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ignore if persistable permission is not granted
            }
            selectedUri = uri
            selectedName = guessFileName(uri) ?: "archivo.csv"
            binding.tvSelectedFile.text = selectedName
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentImportFormBinding.bind(view)
        cacheStore = CacheStore.getInstance(requireContext())

        binding.tvImportTitle.text = titleLabel
        binding.btnSendCsv.text = sendLabel
        binding.switchDryRun.isChecked = true
        binding.ivImportSectionIcon.setImageResource(R.drawable.addfile)
        applyImportSectionExpanded(expanded = true)

        binding.layoutImportHeader.setOnClickListener {
            applyImportSectionExpanded(!importExpanded)
        }

        val errorAdapter = ImportErrorAdapter(emptyList())
        binding.rvErrors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvErrors.adapter = errorAdapter
        binding.btnClearLocalImportCache.setOnClickListener {
            clearLocalCache(errorAdapter)
        }
        setupPagination(errorAdapter)
        loadCachedErrors(errorAdapter)

        binding.btnSelectFile.setOnClickListener {
            pickCsv.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/*"))
        }

        binding.btnSendCsv.setOnClickListener {
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
            val fuzzy = binding.etFuzzy.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.9
            if (fuzzy < 0.0 || fuzzy > 1.0) {
                CreateUiFeedback.showErrorPopup(
                    activity = requireActivity(),
                    title = "Valor invalido",
                    details = "Los valores deben estar entre 0 y 1.",
                    animationRes = R.raw.error
                )
                return@setOnClickListener
            }
            val dryRun = binding.switchDryRun.isChecked
            upload(uri, selectedName ?: "import.csv", dryRun, fuzzy, errorAdapter)
        }
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
                binding.tvSummary.text = cached.summary
                if (allErrorRows.isNotEmpty()) {
                    currentOffset = ((allErrorRows.size - 1) / pageSize) * pageSize
                }
                renderPage(errorAdapter)
            }
        }
    }

    private fun upload(
        uri: Uri,
        fileName: String,
        dryRun: Boolean,
        fuzzy: Double,
        errorAdapter: ImportErrorAdapter
    ) {
        binding.progressImport.visibility = View.VISIBLE
        binding.btnSendCsv.isEnabled = false

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
                    showSummary(res, errorAdapter)
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
                binding.progressImport.visibility = View.GONE
                binding.btnSendCsv.isEnabled = true
            }
        }
    }

    private fun showImportError(details: String) {
        val detailLower = details.lowercase()
        val isOfflineError = detailLower.contains("offline")
            || detailLower.contains("backend unavailable")
            || detailLower.contains("manual offline mode")
            || detailLower.contains("sin conexión")
            || detailLower.contains("sin conexion")
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

    private fun showSummary(res: ImportSummaryResponseDto, errorAdapter: ImportErrorAdapter) {
        val mode = if (res.dry_run) "Dry-run" else "Importacion"
        binding.tvSummary.text =
            "$mode completado\n" +
            "Total: ${res.total_rows} | OK: ${res.ok_rows} | Errores: ${res.error_rows} | Reviews: ${res.review_rows}"

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
        renderPage(errorAdapter)
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
        applyImportSectionExpanded(expanded = false)
    }

    private fun renderPage(errorAdapter: ImportErrorAdapter) {
        if (_binding == null) return
        val total = allErrorRows.size
        if (total == 0) {
            binding.tvErrorsEmpty.visibility = View.VISIBLE
            binding.tvErrorsPageInfo.text = "Mostrando 0/0"
            binding.btnPrevErrors.isEnabled = false
            binding.btnNextErrors.isEnabled = false
            errorAdapter.submit(emptyList())
            return
        }

        binding.tvErrorsEmpty.visibility = View.GONE
        val from = currentOffset.coerceAtMost((total - 1).coerceAtLeast(0))
        val to = (from + pageSize).coerceAtMost(total)
        // Use a snapshot list to avoid invalidating adapter data when allErrorRows mutates.
        val pageItems = allErrorRows.subList(from, to).toList()
        errorAdapter.submit(pageItems)

        binding.tvErrorsPageInfo.text = "Mostrando $to/$total"
        binding.btnPrevErrors.isEnabled = from > 0
        binding.btnNextErrors.isEnabled = to < total
    }

    private fun saveUiCache() {
        lifecycleScope.launch {
            val key = "$cacheKeyPrefix:import_ui_state"
            cacheStore.put(
                key,
                ImportUiCacheDto(
                    rows = allErrorRows,
                    summary = binding.tvSummary.text?.toString() ?: "Resultado: -"
                )
            )
        }
    }

    private fun clearLocalCache(errorAdapter: ImportErrorAdapter) {
        lifecycleScope.launch {
            allErrorRows.clear()
            currentOffset = 0
            binding.tvSummary.text = "Resultado: -"
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

    private fun applyImportSectionExpanded(expanded: Boolean) {
        importExpanded = expanded
        binding.layoutImportContent.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.ivImportSectionToggle.setImageResource(if (expanded) R.drawable.up else R.drawable.down)
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
        _binding = null
    }
}
