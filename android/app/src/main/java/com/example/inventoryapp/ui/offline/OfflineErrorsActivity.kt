package com.example.inventoryapp.ui.offline

import android.os.Bundle
import android.text.format.DateFormat
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.FailedRequest
import com.example.inventoryapp.databinding.ActivityOfflineErrorsBinding

class OfflineErrorsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineErrorsBinding
    private lateinit var q: OfflineQueue
    private var failed: List<FailedRequest> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineErrorsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        q = OfflineQueue(this)

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

        binding.lvFailed.setOnItemClickListener { _, _, position, _ ->
            showFailedActions(position)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        failed = q.getFailed()
        binding.tvCount.text = "${failed.size} errores"

        val lines = failed.map { f ->
            val whenStr = DateFormat.format("dd/MM HH:mm", f.failedAt).toString()
            val codeStr = f.httpCode?.toString() ?: "-"
            "[$whenStr] ${f.original.type} (HTTP $codeStr)\n${f.errorMessage}"
        }

        binding.lvFailed.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lines)
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
