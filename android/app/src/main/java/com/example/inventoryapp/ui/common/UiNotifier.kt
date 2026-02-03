package com.example.inventoryapp.ui.common

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog

object UiNotifier {

    fun show(activity: Activity, message: String, duration: Int = Snackbar.LENGTH_LONG) {
        val root = activity.findViewById<View>(android.R.id.content)
        val snack = Snackbar.make(root, message, duration)
        applyStyle(activity, snack)
        snack.show()
    }

    fun showBlocking(activity: Activity, title: String, message: String, iconRes: Int? = null) {
        val builder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
        if (iconRes != null) {
            builder.setIcon(iconRes)
        }
        builder.show()
    }

    private fun applyStyle(activity: Activity, snack: Snackbar) {
        val view = snack.view
        view.background = ContextCompat.getDrawable(activity, R.drawable.bg_snackbar)
        val text = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        text.setTextColor(Color.WHITE)
        text.maxLines = 4
    }
}
