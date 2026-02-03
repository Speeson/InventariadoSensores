package com.example.inventoryapp.ui.common

import android.view.View
import android.graphics.Color
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R
import com.google.android.material.snackbar.Snackbar

class SendSnack(private val root: View) {
    private var sending: Snackbar? = null

    fun showSending(msg: String = "Enviando...") {
        sending?.dismiss()
        sending = Snackbar.make(root, msg, Snackbar.LENGTH_INDEFINITE).also {
            applyStyle(it)
            it.show()
        }
    }

    fun showSuccess(msg: String) {
        sending?.dismiss()
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).also {
            applyStyle(it)
            it.show()
        }
    }

    fun showQueuedOffline(msg: String) {
        sending?.dismiss()
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).also {
            applyStyle(it)
            it.show()
        }
    }

    fun showError(msg: String) {
        sending?.dismiss()
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).also {
            applyStyle(it)
            it.show()
        }
    }

    private fun applyStyle(snack: Snackbar) {
        val view = snack.view
        view.background = ContextCompat.getDrawable(root.context, R.drawable.bg_snackbar)
        val text = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        text.setTextColor(Color.WHITE)
        text.maxLines = 4
    }
}
