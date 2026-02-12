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
    private fun canShowDialog(activity: Activity): Boolean {
        return !activity.isFinishing && !activity.isDestroyed
    }

    private fun dismissSafely(dialog: AlertDialog) {
        runCatching {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }

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
                CreateUiFeedback.dismissSafely(dialog)
            }, waitMs)
        }

        fun dismissThen(onDismiss: () -> Unit) {
            if (dismissed) return
            dismissed = true
            val elapsed = SystemClock.elapsedRealtime() - shownAtMs
            val waitMs = (minCycleMs.get() - elapsed).coerceAtLeast(0)
            handler.postDelayed({
                CreateUiFeedback.dismissSafely(dialog)
                onDismiss()
            }, waitMs)
        }
    }

    fun showLoading(activity: Activity, entityLabel: String): LoadingHandle {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_create_loading, null)
        val label = view.findViewById<TextView>(R.id.tvLoadingLabel)
        val lottie = view.findViewById<LottieAnimationView>(R.id.lottieLoading)
        label.text = "Creando $entityLabel..."

        val minCycleMs = AtomicLong(1_200L)
        lottie.scaleX = 2f
        lottie.scaleY = 2f
        lottie.repeatCount = LottieDrawable.INFINITE
        lottie.addLottieOnCompositionLoadedListener { comp ->
            minCycleMs.set(comp.duration.toLong().coerceAtLeast(1_200L))
        }
        lottie.playAnimation()

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (canShowDialog(activity)) {
            runCatching { dialog.show() }
        }
        return LoadingHandle(dialog, minCycleMs, SystemClock.elapsedRealtime(), Handler(Looper.getMainLooper()))
    }

    fun showLoadingMessage(
        activity: Activity,
        message: String,
        animationRes: Int = R.raw.loading
    ): LoadingHandle {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_create_loading, null)
        val label = view.findViewById<TextView>(R.id.tvLoadingLabel)
        val lottie = view.findViewById<LottieAnimationView>(R.id.lottieLoading)
        label.text = message

        val minCycleMs = AtomicLong(1_200L)
        lottie.setAnimation(animationRes)
        lottie.scaleX = 2f
        lottie.scaleY = 2f
        lottie.repeatCount = LottieDrawable.INFINITE
        lottie.addLottieOnCompositionLoadedListener { comp ->
            minCycleMs.set(comp.duration.toLong().coerceAtLeast(1_200L))
        }
        lottie.playAnimation()

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (canShowDialog(activity)) {
            runCatching { dialog.show() }
        }
        return LoadingHandle(dialog, minCycleMs, SystemClock.elapsedRealtime(), Handler(Looper.getMainLooper()))
    }

    fun showListLoading(
        activity: Activity,
        message: String,
        animationRes: Int = R.raw.loading_list,
        minCycles: Int = 3
    ): LoadingHandle {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_list_loading, null)
        val label = view.findViewById<TextView>(R.id.tvListLoadingLabel)
        val lottie = view.findViewById<LottieAnimationView>(R.id.lottieListLoading)
        label.text = message

        val minCycleMs = AtomicLong(2_400L)
        lottie.setAnimation(animationRes)
        lottie.scaleX = 5f
        lottie.scaleY = 5f
        lottie.repeatCount = LottieDrawable.INFINITE
        lottie.addLottieOnCompositionLoadedListener { comp ->
            val cycles = if (minCycles <= 0) 1 else minCycles
            minCycleMs.set((comp.duration.toLong() * cycles).coerceAtLeast(1_200L))
        }
        lottie.playAnimation()

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (canShowDialog(activity)) {
            runCatching { dialog.show() }
        }
        return LoadingHandle(dialog, minCycleMs, SystemClock.elapsedRealtime(), Handler(Looper.getMainLooper()))
    }

    private fun showAnimatedResultPopup(
        activity: Activity,
        title: String,
        details: String,
        layoutRes: Int,
        animationRes: Int,
        autoDismissMs: Long = 2800L,
        accentColorRes: Int? = null
    ) {
        if (!canShowDialog(activity)) return

        val view = LayoutInflater.from(activity).inflate(layoutRes, null)
        val titleView = view.findViewById<TextView>(R.id.tvSuccessTitle)
        val detailsView = view.findViewById<TextView>(R.id.tvSuccessDetails)
        val lottie = view.findViewById<LottieAnimationView>(R.id.lottieSuccess)

        titleView.text = title
        if (accentColorRes != null) {
            titleView.setTextColor(activity.getColor(accentColorRes))
        }
        detailsView.text = details
        lottie.setAnimation(animationRes)
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
        val wasShown = runCatching {
            dialog.show()
            true
        }.getOrDefault(false)
        if (!wasShown) return

        view.setOnClickListener { dismissSafely(dialog) }
        if (autoDismissMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                dismissSafely(dialog)
            }, autoDismissMs)
        }
    }

    fun showCreatedPopup(
        activity: Activity,
        title: String,
        details: String,
        autoDismissMs: Long = 2800L,
        accentColorRes: Int? = null
    ) {
        showAnimatedResultPopup(
            activity = activity,
            title = title,
            details = details,
            layoutRes = R.layout.dialog_create_success,
            animationRes = R.raw.correct_create,
            autoDismissMs = autoDismissMs,
            accentColorRes = accentColorRes
        )
    }

    fun showErrorPopup(
        activity: Activity,
        title: String,
        details: String,
        autoDismissMs: Long = 3200L,
        accentColorRes: Int? = null
    ) {
        showAnimatedResultPopup(
            activity = activity,
            title = title,
            details = details,
            layoutRes = R.layout.dialog_create_failure,
            animationRes = R.raw.wrong,
            autoDismissMs = autoDismissMs,
            accentColorRes = accentColorRes
        )
    }
}
