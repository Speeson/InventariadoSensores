package com.example.inventoryapp.ui.common

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.app.Dialog
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog
import android.content.Context
import com.example.inventoryapp.BuildConfig

object UiNotifier {
    private fun canShowDialog(activity: Activity): Boolean {
        return !activity.isFinishing && !activity.isDestroyed
    }

    private fun dismissSafely(dialog: Dialog) {
        runCatching {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }

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

    fun showBlockingTimed(
        activity: Activity,
        message: String,
        iconRes: Int,
        timeoutMs: Long = 10_000L
    ) {
        if (!canShowDialog(activity)) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_important_notice, null)
        val icon = view.findViewById<ImageView>(R.id.noticeIcon)
        val text = view.findViewById<TextView>(R.id.noticeMessage)
        icon.setImageResource(iconRes)
        text.text = message

        val dialog = Dialog(activity)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        val handler = Handler(Looper.getMainLooper())
        val dismissRunnable = Runnable {
            dismissSafely(dialog)
        }
        dialog.setOnDismissListener {
            handler.removeCallbacks(dismissRunnable)
        }
        val wasShown = runCatching {
            dialog.show()
            true
        }.getOrDefault(false)
        if (!wasShown) return
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.CENTER)
        view.findViewById<android.widget.Button>(R.id.noticeAccept)?.setOnClickListener {
            dismissSafely(dialog)
        }
        handler.postDelayed(dismissRunnable, timeoutMs)
    }

    fun showCentered(activity: Activity, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(activity, message, duration)
        toast.setGravity(Gravity.CENTER, 0, 0)
        val view = toast.view
        if (view != null) {
            view.background = ContextCompat.getDrawable(activity, R.drawable.bg_snackbar)
            val text = view.findViewById<TextView>(android.R.id.message)
            text?.setTextColor(Color.WHITE)
        }
        toast.show()
    }

    private fun applyStyle(activity: Activity, snack: Snackbar) {
        val view = snack.view
        view.background = ContextCompat.getDrawable(activity, R.drawable.bg_snackbar)
        val text = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        text.setTextColor(Color.WHITE)
        text.maxLines = 4
    }

    fun showConnectionError(activity: Activity, detail: String? = null, allowTechnical: Boolean = true) {
        show(activity, buildConnectionMessage(activity, detail, allowTechnical))
    }

    fun buildConnectionMessage(context: Context, detail: String? = null, allowTechnical: Boolean = true): String {
        val friendly = "No se pudo conectar. Revisa la IP o la red."
        if (detail.isNullOrBlank()) return friendly
        return if (allowTechnical && shouldShowTechnical(context)) {
            "Error de conexión: $detail"
        } else {
            friendly
        }
    }

    private fun shouldShowTechnical(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val role = prefs.getString("cached_role", null)
        return role.equals("ADMIN", ignoreCase = true)
    }
}
