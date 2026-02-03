package com.example.inventoryapp.ui.alerts

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingRequest
import com.example.inventoryapp.data.local.FailedRequest
import com.example.inventoryapp.databinding.FragmentOfflinePendingBinding
import com.example.inventoryapp.ui.offline.OfflineErrorsAdapter

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

        binding.tvPendingTitle.text = "Pendientes (${pending.size})"
        binding.tvFailedTitle.text = "Fallidos (${failed.size})"
        binding.tvPendingEmpty.visibility = if (pending.isEmpty()) View.VISIBLE else View.GONE
        binding.tvFailedEmpty.visibility = if (failed.isEmpty()) View.VISIBLE else View.GONE

        pendingAdapter.submit(pending)
        failedAdapter.submit(failed)
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
            }
            .show()
    }
}
