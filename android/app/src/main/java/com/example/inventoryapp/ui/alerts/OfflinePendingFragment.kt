package com.example.inventoryapp.ui.alerts

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingRequest
import com.example.inventoryapp.data.local.FailedRequest
import com.example.inventoryapp.databinding.FragmentOfflinePendingBinding
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.offline.OfflineErrorsAdapter
import kotlinx.coroutines.launch

class OfflinePendingFragment : Fragment() {

    private var _binding: FragmentOfflinePendingBinding? = null
    private val binding get() = _binding!!

    private lateinit var queue: OfflineQueue
    private lateinit var pendingAdapter: OfflinePendingAdapter
    private lateinit var failedAdapter: OfflineErrorsAdapter

    private var pending: List<PendingRequest> = emptyList()
    private var failed: List<FailedRequest> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOfflinePendingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        queue = OfflineQueue(requireContext())

        pendingAdapter = OfflinePendingAdapter(emptyList()) { index -> showPendingActions(index) }
        binding.rvPending.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPending.adapter = pendingAdapter

        failedAdapter = OfflineErrorsAdapter { index -> showFailedActions(index) }
        binding.rvFailed.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFailed.adapter = failedAdapter

        binding.layoutPendingHeader.setOnClickListener {
            toggleSection(binding.layoutPendingContent, binding.ivPendingChevron)
        }
        binding.layoutFailedHeader.setOnClickListener {
            toggleSection(binding.layoutFailedContent, binding.ivFailedChevron)
        }
        binding.btnClearPending.setOnClickListener {
            queue.clear()
            refresh()
        }
        binding.btnClearFailed.setOnClickListener {
            queue.clearFailed()
            refresh()
        }

        GradientIconUtil.applyGradient(binding.ivPendingChevron, com.example.inventoryapp.R.drawable.triangle_down_lg)
        GradientIconUtil.applyGradient(binding.ivFailedChevron, com.example.inventoryapp.R.drawable.triangle_down_lg)

        // Offline sections collapsed by default.
        binding.layoutPendingContent.visibility = View.GONE
        binding.layoutFailedContent.visibility = View.GONE
        binding.ivPendingChevron.rotation = 0f
        binding.ivFailedChevron.rotation = 0f
        binding.layoutPendingHeader.setBackgroundResource(com.example.inventoryapp.R.drawable.bg_toggle_idle)
        binding.layoutFailedHeader.setBackgroundResource(com.example.inventoryapp.R.drawable.bg_toggle_idle)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        pending = queue.getAll()
        failed = queue.getFailed()

        binding.tvPendingTitle.text = "Envíos pendientes (${pending.size})"
        binding.tvFailedTitle.text = "Envíos fallidos (${failed.size})"
        binding.tvPendingEmpty.visibility = if (pending.isEmpty()) View.VISIBLE else View.GONE
        binding.tvFailedEmpty.visibility = if (failed.isEmpty()) View.VISIBLE else View.GONE

        pendingAdapter.submit(pending)
        failedAdapter.submit(failed)
    }

    private fun toggleSection(content: View, chevron: View) {
        val isPending = content === binding.layoutPendingContent
        val header = if (isPending) binding.layoutPendingHeader else binding.layoutFailedHeader
        val otherContent = if (isPending) binding.layoutFailedContent else binding.layoutPendingContent
        val otherChevron = if (isPending) binding.ivFailedChevron else binding.ivPendingChevron
        val otherHeader = if (isPending) binding.layoutFailedHeader else binding.layoutPendingHeader

        val opening = !content.isVisible
        content.visibility = if (opening) View.VISIBLE else View.GONE
        chevron.animate().rotation(if (opening) 180f else 0f).setDuration(160).start()

        otherContent.visibility = View.GONE
        otherChevron.animate().rotation(0f).setDuration(160).start()

        header.setBackgroundResource(
            if (opening) com.example.inventoryapp.R.drawable.bg_toggle_active else com.example.inventoryapp.R.drawable.bg_toggle_idle
        )
        otherHeader.setBackgroundResource(com.example.inventoryapp.R.drawable.bg_toggle_idle)
    }

    private fun showPendingActions(index: Int) {
        val item = pending[index]
        val whenStr = DateFormat.format("dd/MM/yyyy HH:mm:ss", item.createdAt).toString()

        AlertDialog.Builder(requireContext())
            .setTitle("Pendiente offline")
            .setMessage(
                "Tipo: ${item.type}\n" +
                    "Fecha: $whenStr\n\n" +
                    "Payload:\n${item.payloadJson}"
            )
            .setNegativeButton("Cerrar", null)
            .setPositiveButton("Borrar") { _, _ ->
                val list = pending.toMutableList()
                if (index in list.indices) {
                    list.removeAt(index)
                    queue.saveAll(list)
                    refresh()
                }
            }
            .show()
    }

    private fun showFailedActions(index: Int) {
        val item = failed[index]
        val whenStr = DateFormat.format("dd/MM/yyyy HH:mm:ss", item.failedAt).toString()
        val codeStr = item.httpCode?.toString() ?: "-"

        AlertDialog.Builder(requireContext())
            .setTitle("Error offline")
            .setMessage(
                "Tipo: ${item.original.type}\n" +
                    "HTTP: $codeStr\n" +
                    "Fecha: $whenStr\n\n" +
                    "Mensaje:\n${item.errorMessage}\n\n" +
                    "Payload:\n${item.original.payloadJson}"
            )
            .setNegativeButton("Cerrar", null)
            .setNeutralButton("Borrar") { _, _ ->
                queue.removeFailedAt(index)
                refresh()
            }
            .setPositiveButton("Reintentar") { _, _ ->
                queue.moveFailedBackToPending(index)
                refresh()
                retryPendingNow()
            }
            .show()
    }

    private fun retryPendingNow() {
        val loading = CreateUiFeedback.showLoadingMessage(
            activity = requireActivity(),
            message = "Reintentando envío offline...",
            animationRes = R.raw.sync,
            animationScale = 1.15f
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val report = OfflineSyncer.flush(requireContext())
            refresh()
            loading.dismissThen {
                val failedAgain = report.movedToFailed > 0 || (report.sent == 0 && report.stoppedReason != null)
                if (failedAgain) {
                    val details = if (report.movedToFailed > 0) {
                        if (report.movedToFailed == 1) {
                            "El envío ha vuelto a fallar. Revisa Pendientes offline."
                        } else {
                            "${report.movedToFailed} envíos han vuelto a fallar. Revisa Pendientes offline."
                        }
                    } else {
                        "No se pudo reintentar el envío en este momento. Revisa Pendientes offline."
                    }
                    CreateUiFeedback.showErrorPopup(
                        activity = requireActivity(),
                        title = "Reintento fallido",
                        details = details,
                        animationRes = R.raw.wrong
                    )
                } else if (report.sent > 0) {
                    CreateUiFeedback.showStatusPopup(
                        activity = requireActivity(),
                        title = "Reintento completado",
                        details = if (report.sent == 1) {
                            "Se ha reenviado correctamente 1 envío offline."
                        } else {
                            "Se han reenviado correctamente ${report.sent} envíos offline."
                        },
                        animationRes = R.raw.sync
                    )
                }
            }
        }
    }
}
