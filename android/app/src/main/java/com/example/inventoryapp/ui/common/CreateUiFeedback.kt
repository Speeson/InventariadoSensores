package com.example.inventoryapp.ui.common

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.example.inventoryapp.R
import java.util.concurrent.atomic.AtomicLong

object CreateUiFeedback {

    class LoadingHandle internal constructor(
        private val dialog: AlertDialog,
        private val minCycleMs: AtomicLong,
        private val shownAtMs: Long,
        private val handler: Handler
    ) {
        private var dismissed = false

        fun dismiss() {
            if (dismissed) return
            dismissed = true
            val elapsed = SystemClock.elapsedRealtime() - shownAtMs
            val waitMs = (minCycleMs.get() - elapsed).coerceAtLeast(0)
            handler.postDelayed({
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }, waitMs)
        }

        fun dismissThen(onDismiss: () -> Unit) {
            if (dismissed) return
            dismissed = true
            val elapsed = SystemClock.elapsedRealtime() - shownAtMs
            val waitMs = (minCycleMs.get() - elapsed).coerceAtLeast(0)
            handler.postDelayed({
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                onDismiss()
            }, waitMs)
        }
    }

    fun showLoading(activity: Activity, entityLabel: String): LoadingHandle {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_create_loading, null)
        val label = view.findViewById<TextView>(R.id.tvLoadingLabel)
        val lottie = view.findViewById<LottieAnimationView>(R.id.lottieLoading)
        label.text = "Creando $entityLabel..."

        val minCycleMs = AtomicLong(800L)
        lottie.repeatCount = LottieDrawable.INFINITE
        lottie.addLottieOnCompositionLoadedListener { comp ->
            minCycleMs.set(comp.duration.toLong())
        }
        lottie.playAnimation()

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        return LoadingHandle(dialog, minCycleMs, SystemClock.elapsedRealtime(), Handler(Looper.getMainLooper()))
    }

    fun showCreatedPopup(
        activity: Activity,
        title: String,
        details: String,
        autoDismissMs: Long = 2800L,
        accentColorRes: Int? = null
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_create_success, null)
        val titleView = view.findViewById<TextView>(R.id.tvSuccessTitle)
        val detailsView = view.findViewById<TextView>(R.id.tvSuccessDetails)
        val lottie = view.findViewById<LottieAnimationView>(R.id.lottieSuccess)

        titleView.text = title
        if (accentColorRes != null) {
            titleView.setTextColor(activity.getColor(accentColorRes))
        }
        detailsView.text = details
        lottie.repeatCount = 0
        lottie.playAnimation()
        lottie.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                lottie.progress = 1f
            }
        })

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        view.setOnClickListener { dialog.dismiss() }
        if (autoDismissMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (dialog.isShowing) dialog.dismiss()
            }, autoDismissMs)
        }
    }
}
