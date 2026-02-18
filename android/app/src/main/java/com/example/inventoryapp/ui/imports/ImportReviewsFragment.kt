package com.example.inventoryapp.ui.imports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ImportReviewItemDto
import com.example.inventoryapp.databinding.FragmentImportReviewsBinding
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.GsonBuilder
import kotlinx.coroutines.launch

class ImportReviewsFragment : Fragment(R.layout.fragment_import_reviews) {

    private var _binding: FragmentImportReviewsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ImportReviewAdapter
    private lateinit var cacheStore: CacheStore
    private val allReviews = mutableListOf<ImportReviewItemDto>()
    private val pageSize = 5
    private var currentOffset = 0

    data class ImportReviewsCacheDto(
        val items: List<ImportReviewItemDto>
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentImportReviewsBinding.bind(view)
        cacheStore = CacheStore.getInstance(requireContext())

        adapter = ImportReviewAdapter(emptyList()) { review ->
            showReviewBottomSheet(review)
        }
        binding.rvReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReviews.adapter = adapter

        binding.btnPrevReviews.setOnClickListener {
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            renderPage()
        }
        binding.btnNextReviews.setOnClickListener {
            if (currentOffset + pageSize >= allReviews.size) return@setOnClickListener
            currentOffset += pageSize
            renderPage()
        }

        binding.btnRefreshReviews.setOnClickListener { loadReviews(showFeedback = true) }
        binding.btnRejectAllReviews.setOnClickListener { confirmRejectAll() }
        loadCachedReviews()
        loadReviews(showFeedback = false)
    }

    private fun confirmRejectAll() {
        if (allReviews.isEmpty()) {
            CreateUiFeedback.showErrorPopup(
                activity = requireActivity(),
                title = "Sin revisiones",
                details = "No hay revisiones para rechazar.",
                animationRes = R.raw.notfound
            )
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rechazar todas")
            .setMessage("Se rechazaran ${allReviews.size} revisiones. Esta accion no se puede deshacer. Continuar?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Rechazar") { _, _ ->
                rejectAllReviews()
            }
            .show()
    }

    private fun rejectAllReviews() {
        lifecycleScope.launch {
            binding.btnRejectAllReviews.isEnabled = false
            binding.btnRefreshReviews.isEnabled = false
            var rejected = 0
            var failed = 0
            val ids = allReviews.map { it.id }
            for (id in ids) {
                try {
                    val res = NetworkModule.api.rejectImportReview(id)
                    if (res.isSuccessful) rejected++ else failed++
                } catch (_: Exception) {
                    failed++
                }
            }
            binding.btnRejectAllReviews.isEnabled = true
            binding.btnRefreshReviews.isEnabled = true

            loadReviews(showFeedback = false)
            if (failed == 0) {
                CreateUiFeedback.showStatusPopup(
                    activity = requireActivity(),
                    title = "Revisiones rechazadas",
                    details = "Se rechazaron $rejected revisiones correctamente.",
                    animationRes = R.raw.correct_create
                )
            } else {
                CreateUiFeedback.showErrorPopup(
                    activity = requireActivity(),
                    title = "Rechazo parcial",
                    details = "Rechazadas: $rejected. Fallidas: $failed.",
                    animationRes = R.raw.error
                )
            }
        }
    }

    private fun loadReviews(showFeedback: Boolean) {
        lifecycleScope.launch {
            try {
                val merged = mutableListOf<ImportReviewItemDto>()
                var offset = 0
                val limit = 100
                var total = Int.MAX_VALUE

                while (offset < total) {
                    val res = NetworkModule.api.listImportReviews(limit = limit, offset = offset)
                    if (!res.isSuccessful || res.body() == null) {
                        showError(ApiErrorFormatter.format(res.code()))
                        return@launch
                    }
                    val body = res.body()!!
                    merged.addAll(body.items)
                    total = body.total
                    offset += body.items.size
                    if (body.items.isEmpty()) break
                }

                allReviews.clear()
                allReviews.addAll(merged.sortedBy { it.id })
                currentOffset = if (allReviews.isNotEmpty()) {
                    ((allReviews.size - 1) / pageSize) * pageSize
                } else {
                    0
                }
                saveReviewsCache()
                renderPage()
                if (showFeedback) {
                    if (allReviews.isEmpty()) {
                        CreateUiFeedback.showErrorPopup(
                            activity = requireActivity(),
                            title = "Sin revisiones",
                            details = "No hay revisiones para mostrar.",
                            animationRes = R.raw.notfound
                        )
                    } else {
                        CreateUiFeedback.showStatusPopup(
                            activity = requireActivity(),
                            title = "Revisiones cargadas",
                            details = "Se han cargado las revisiones correctamente.",
                            animationRes = R.raw.correct_create
                        )
                    }
                }
            } catch (e: Exception) {
                val cached = cacheStore.get("imports_reviews:list", ImportReviewsCacheDto::class.java)
                if (cached != null) {
                    allReviews.clear()
                    allReviews.addAll(cached.items.sortedBy { it.id })
                    currentOffset = currentOffset.coerceAtMost(((allReviews.size - 1).coerceAtLeast(0) / pageSize) * pageSize)
                    renderPage()
                }
                showError(e.message ?: "Error de importacion")
            }
        }
    }

    private fun renderPage() {
        if (_binding == null) return
        val total = allReviews.size
        if (total == 0) {
            binding.tvReviewsInfo.text = "0 revisiones"
            binding.btnPrevReviews.isEnabled = false
            binding.btnNextReviews.isEnabled = false
            adapter.submit(emptyList())
            return
        }

        val from = currentOffset.coerceAtMost((total - 1).coerceAtLeast(0))
        val to = (from + pageSize).coerceAtMost(total)
        adapter.submit(allReviews.subList(from, to))

        binding.tvReviewsInfo.text = "Mostrando $to/$total revisiones"
        binding.btnPrevReviews.isEnabled = from > 0
        binding.btnNextReviews.isEnabled = to < total
    }

    private fun loadCachedReviews() {
        lifecycleScope.launch {
            val cached = cacheStore.get("imports_reviews:list", ImportReviewsCacheDto::class.java) ?: return@launch
            allReviews.clear()
            allReviews.addAll(cached.items.sortedBy { it.id })
            if (allReviews.isNotEmpty()) {
                currentOffset = ((allReviews.size - 1) / pageSize) * pageSize
            }
            renderPage()
        }
    }

    private fun saveReviewsCache() {
        lifecycleScope.launch {
            cacheStore.put("imports_reviews:list", ImportReviewsCacheDto(items = allReviews))
        }
    }

    private fun showReviewBottomSheet(review: ImportReviewItemDto) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_import_review_bottom_sheet, null, false)
        dialog.setContentView(sheet)

        val tvTitle = sheet.findViewById<TextView>(R.id.tvReviewTitle)
        val tvReason = sheet.findViewById<TextView>(R.id.tvReviewReason)
        val layoutFields = sheet.findViewById<ViewGroup>(R.id.layoutFields)
        val tvSuggestions = sheet.findViewById<TextView>(R.id.tvSuggestions)
        val btnToggleJson = sheet.findViewById<View>(R.id.btnToggleJson)
        val tvJson = sheet.findViewById<TextView>(R.id.tvJson)
        val btnApprove = sheet.findViewById<View>(R.id.btnApprove)
        val btnReject = sheet.findViewById<View>(R.id.btnReject)

        tvTitle.text = "Review #${review.id} - Fila ${review.row_number}"
        tvReason.text = review.reason

        layoutFields.removeAllViews()
        addField(layoutFields, "SKU", review.payload["sku"])
        addField(layoutFields, "Barcode", review.payload["barcode"])
        addField(layoutFields, "Nombre", review.payload["name"])
        addField(layoutFields, "Categoria", review.payload["category_id"])
        addField(layoutFields, "Location", review.payload["location_id"])
        addField(layoutFields, "Tipo", review.payload["type"])
        addField(layoutFields, "Cantidad", review.payload["quantity"])
        addField(layoutFields, "Origen", review.payload["from_location_id"])
        addField(layoutFields, "Destino", review.payload["to_location_id"])

        val suggestionsText = buildSuggestions(review)
        tvSuggestions.text = suggestionsText.ifBlank { "Sin sugerencias" }

        val gson = GsonBuilder().setPrettyPrinting().create()
        tvJson.text = gson.toJson(review.payload)

        btnToggleJson.setOnClickListener {
            tvJson.visibility = if (tvJson.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnApprove.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val res = NetworkModule.api.approveImportReview(review.id)
                    if (!res.isSuccessful) {
                        showError(ApiErrorFormatter.format(res.code()))
                        return@launch
                    }
                    CreateUiFeedback.showStatusPopup(
                        activity = requireActivity(),
                        title = "Review aprobada",
                        details = "Review aprobada",
                        animationRes = R.raw.correct_create
                    )
                    dialog.dismiss()
                    if (allReviews.size == 1 && currentOffset > 0) {
                        currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
                    }
                    loadReviews(showFeedback = false)
                } catch (e: Exception) {
                    showError(e.message ?: "Error de importacion")
                }
            }
        }

        btnReject.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val res = NetworkModule.api.rejectImportReview(review.id)
                    if (!res.isSuccessful) {
                        showError(ApiErrorFormatter.format(res.code()))
                        return@launch
                    }
                    CreateUiFeedback.showErrorPopup(
                        activity = requireActivity(),
                        title = "Review rechazada",
                        details = "Review rechazada",
                        animationRes = R.raw.wrong
                    )
                    dialog.dismiss()
                    if (allReviews.size == 1 && currentOffset > 0) {
                        currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
                    }
                    loadReviews(showFeedback = false)
                } catch (e: Exception) {
                    showError(e.message ?: "Error de importacion")
                }
            }
        }

        dialog.show()
    }

    private fun showError(details: String) {
        val isNotFound = details.lowercase().contains("no encontrado")
        CreateUiFeedback.showErrorPopup(
            activity = requireActivity(),
            title = if (isNotFound) "No encontrado" else "Error de importacion",
            details = details,
            animationRes = if (isNotFound) R.raw.notfound else R.raw.error
        )
    }

    private fun addField(container: ViewGroup, label: String, value: Any?) {
        if (value == null) return
        val tv = TextView(requireContext())
        tv.text = "$label: ${value.toString()}"
        tv.textSize = 14f
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        tv.setPadding(0, 2, 0, 2)
        container.addView(tv)
    }

    private fun buildSuggestions(review: ImportReviewItemDto): String {
        val suggestions = review.suggestions ?: return ""
        val matches = suggestions["matches"] as? List<*> ?: return ""
        val lines = matches.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"]?.toString() ?: "-"
            val sku = map["sku"]?.toString() ?: "-"
            val barcode = map["barcode"]?.toString() ?: "-"
            val sim = map["similarity"]?.toString() ?: "-"
            "$name | $sku | $barcode | sim=$sim"
        }
        return lines.joinToString("\n")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
