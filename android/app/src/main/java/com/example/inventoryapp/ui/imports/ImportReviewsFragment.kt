package com.example.inventoryapp.ui.imports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ImportReviewItemDto
import com.example.inventoryapp.databinding.FragmentImportReviewsBinding
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.GsonBuilder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class ImportReviewsFragment : Fragment(R.layout.fragment_import_reviews) {

    private var _binding: FragmentImportReviewsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ImportReviewAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentImportReviewsBinding.bind(view)

        adapter = ImportReviewAdapter(emptyList()) { review ->
            showReviewBottomSheet(review)
        }
        binding.rvReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReviews.adapter = adapter

        binding.btnRefreshReviews.setOnClickListener { loadReviews() }
        loadReviews()
    }

    private fun loadReviews() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listImportReviews()
                if (!res.isSuccessful || res.body() == null) {
                    UiNotifier.show(requireActivity(), ApiErrorFormatter.format(res.code()))
                    return@launch
                }
                val body = res.body()!!
                binding.tvReviewsInfo.text = "${body.total} revisiones"
                adapter.submit(body.items)
            } catch (e: Exception) {
                UiNotifier.show(requireActivity(), "Error: ${e.message}")
            }
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

        tvTitle.text = "Review #${review.id} · Fila ${review.row_number}"
        tvReason.text = review.reason

        layoutFields.removeAllViews()
        addField(layoutFields, "SKU", review.payload["sku"])
        addField(layoutFields, "Barcode", review.payload["barcode"])
        addField(layoutFields, "Nombre", review.payload["name"])
        addField(layoutFields, "Categoría", review.payload["category_id"])
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
                        UiNotifier.show(requireActivity(), ApiErrorFormatter.format(res.code()))
                        return@launch
                    }
                    UiNotifier.show(requireActivity(), "Review aprobada")
                    dialog.dismiss()
                    loadReviews()
                } catch (e: Exception) {
                    UiNotifier.show(requireActivity(), "Error: ${e.message}")
                }
            }
        }

        btnReject.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val res = NetworkModule.api.rejectImportReview(review.id)
                    if (!res.isSuccessful) {
                        UiNotifier.show(requireActivity(), ApiErrorFormatter.format(res.code()))
                        return@launch
                    }
                    UiNotifier.show(requireActivity(), "Review rechazada")
                    dialog.dismiss()
                    loadReviews()
                } catch (e: Exception) {
                    UiNotifier.show(requireActivity(), "Error: ${e.message}")
                }
            }
        }

        dialog.show()
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
            "$name · $sku · $barcode · sim=$sim"
        }
        return lines.joinToString("\n")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
