package com.example.inventoryapp.ui.common

import android.view.View
import com.google.android.material.snackbar.Snackbar

class SendSnack(private val root: View) {
    private var sending: Snackbar? = null

    fun showSending(msg: String = "Enviando...") {
        sending?.dismiss()
        sending = Snackbar.make(root, msg, Snackbar.LENGTH_INDEFINITE).also { it.show() }
    }

    fun showSuccess(msg: String) {
        sending?.dismiss()
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
    }

    fun showQueuedOffline(msg: String) {
        sending?.dismiss()
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
    }

    fun showError(msg: String) {
        sending?.dismiss()
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
    }
}
