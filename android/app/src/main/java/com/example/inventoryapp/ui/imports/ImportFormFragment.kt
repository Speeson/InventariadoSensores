package com.example.inventoryapp.ui.imports

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ImportSummaryResponseDto
import com.example.inventoryapp.databinding.FragmentImportFormBinding
import com.example.inventoryapp.ui.common.UiNotifier
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

abstract class ImportFormFragment : Fragment(R.layout.fragment_import_form) {

    protected abstract val titleLabel: String
    protected abstract val sendLabel: String
    protected abstract suspend fun uploadCsv(
        filePart: MultipartBody.Part,
        dryRun: Boolean,
        fuzzyThreshold: Double
    ): ImportSummaryResponseDto?

    private var _binding: FragmentImportFormBinding? = null
    protected val binding get() = _binding!!

    private var selectedUri: Uri? = null
    private var selectedName: String? = null

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

        binding.tvImportTitle.text = titleLabel
        binding.btnSendCsv.text = sendLabel
        binding.switchDryRun.isChecked = true

        val errorAdapter = ImportErrorAdapter(emptyList())
        binding.rvErrors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvErrors.adapter = errorAdapter

        binding.btnSelectFile.setOnClickListener {
            pickCsv.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/*"))
        }

        binding.btnSendCsv.setOnClickListener {
            val uri = selectedUri
            if (uri == null) {
                UiNotifier.show(requireActivity(), "Selecciona un CSV primero")
                return@setOnClickListener
            }
            val fuzzy = binding.etFuzzy.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.9
            if (fuzzy < 0.0 || fuzzy > 1.0) {
                UiNotifier.show(requireActivity(), "Fuzzy threshold debe estar entre 0 y 1")
                return@setOnClickListener
            }
            val dryRun = binding.switchDryRun.isChecked
            upload(uri, selectedName ?: "import.csv", dryRun, fuzzy, errorAdapter)
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
                    UiNotifier.show(requireActivity(), "CSV vacio")
                    return@launch
                }
                val requestBody = bytes.toRequestBody("text/csv".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
                val res = uploadCsv(part, dryRun, fuzzy)
                if (res != null) {
                    showSummary(res, errorAdapter)
                } else {
                    UiNotifier.show(requireActivity(), "Respuesta vacía del servidor")
                }
            } catch (e: Exception) {
                UiNotifier.show(requireActivity(), "Error: ${e.message}")
            } finally {
                binding.progressImport.visibility = View.GONE
                binding.btnSendCsv.isEnabled = true
            }
        }
    }

    private fun showSummary(res: ImportSummaryResponseDto, errorAdapter: ImportErrorAdapter) {
        val mode = if (res.dry_run) "Dry-run" else "Importación"
        binding.tvSummary.text =
            "$mode completado\n" +
            "Total: ${res.total_rows} · OK: ${res.ok_rows} · Errores: ${res.error_rows} · Reviews: ${res.review_rows}"
        if (res.errors.isEmpty()) {
            binding.tvErrorsEmpty.visibility = View.VISIBLE
            errorAdapter.submit(emptyList())
        } else {
            binding.tvErrorsEmpty.visibility = View.GONE
            errorAdapter.submit(res.errors)
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
        _binding = null
    }
}
