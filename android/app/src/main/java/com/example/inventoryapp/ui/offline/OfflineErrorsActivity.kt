package com.example.inventoryapp.ui.offline

import android.os.Bundle
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.FailedRequest
import com.example.inventoryapp.databinding.ActivityOfflineErrorsBinding


class OfflineErrorsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineErrorsBinding
    private lateinit var q: OfflineQueue
    private var failed: List<FailedRequest> = emptyList()
    private lateinit var adapter: OfflineErrorsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineErrorsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        q = OfflineQueue(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = OfflineErrorsAdapter { index -> showFailedActions(index) }
        binding.rvFailed.layoutManager = LinearLayoutManager(this)
        binding.rvFailed.adapter = adapter

        binding.btnClearAll.setOnClickListener {
            val count = q.getFailed().size
            if (count == 0) return@setOnClickListener

            AlertDialog.Builder(this)
                .setTitle("Borrar todos")
                .setMessage("¿Eliminar los $count errores offline?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar") { _, _ ->
                    q.clearFailed()
                    refresh()
                }
                .show()
        }

    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        failed = q.getFailed()
        binding.tvCount.text = "${failed.size} errores"
        adapter.submit(failed)
    }

    private fun showFailedActions(index: Int) {
        val item = failed[index]
        val whenStr = DateFormat.format("dd/MM/yyyy HH:mm:ss", item.failedAt).toString()
        val codeStr = item.httpCode?.toString() ?: "-"

        AlertDialog.Builder(this)
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
                q.removeFailedAt(index)
                refresh()
            }
            .setPositiveButton("Reintentar") { _, _ ->
                // Lo devuelve a pending y lo quita de failed
                q.moveFailedBackToPending(index)
                refresh()
            }
            .show()
    }
}
