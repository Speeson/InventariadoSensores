package com.example.inventoryapp.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.inventoryapp.R
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.ActivityTracker
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.SystemAlertManager
import com.example.inventoryapp.ui.common.UiNotifier

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        return createForegroundInfo()
    }

    override suspend fun doWork(): Result {
        if (shouldUseForeground(applicationContext)) {
            setForeground(createForegroundInfo())
        }

        val currentActivity = ActivityTracker.getCurrent()
        if (currentActivity != null && currentActivity !is LoginActivity) {
            // In foreground, let NetworkModule handle sync + UX to avoid races/duplicates.
            return Result.success()
        }

        val report = OfflineSyncer.flush(applicationContext)
        showForegroundSyncResult(report)

        val reason = report.stoppedReason
        if (report.sent > 0 || reason != null) {
            val msg = if (reason == null) {
                "Enviados ${report.sent} pendientes offline."
            } else {
                "Enviados ${report.sent}. Motivo: $reason"
            }
            SystemAlertManager.record(
                applicationContext,
                SystemAlertType.OFFLINE_SYNC_OK,
                "Sincronizacion offline",
                msg,
                blocking = false
            )
        }

        if (reason == null) return Result.success()

        return when {
            reason.startsWith("NO_TOKEN") -> Result.success()
            reason.startsWith("NO_BACKEND") -> Result.retry()
            reason.startsWith("IOEXCEPTION") -> Result.retry()
            reason.startsWith("EXCEPTION") -> Result.retry()
            reason.startsWith("STOPPED_WITH_") -> Result.retry()
            else -> Result.success()
        }
    }

    private fun showForegroundSyncResult(report: OfflineSyncer.FlushReport) {
        if (report.sent <= 0 && report.movedToFailed <= 0) return

        val activity = ActivityTracker.getCurrent() ?: return
        if (activity is LoginActivity) return

        val (title, message, iconRes) = when {
            report.sent > 0 && report.movedToFailed > 0 -> Triple(
                "Sincronización offline parcial",
                "Se sincronizaron ${report.sent} elementos offline y ${report.movedToFailed} fallaron. Revisa Pendientes offline.",
                R.drawable.ic_error_red
            )
            report.sent > 0 -> Triple(
                "Pendientes procesados",
                "Se han enviado correctamente ${report.sent} pendientes offline.",
                R.drawable.ic_check_green
            )
            else -> Triple(
                "Pendientes con error",
                if (report.movedToFailed == 1) {
                    "1 pendiente ha fallado. Revisa la pestaña de Pendientes offline."
                } else {
                    "${report.movedToFailed} pendientes han fallado. Revisa la pestaña de Pendientes offline."
                },
                R.drawable.ic_error_red
            )
        }

        activity.runOnUiThread {
            if (title == "Pendientes procesados") {
                CreateUiFeedback.showStatusPopup(
                    activity = activity,
                    title = title,
                    details = message,
                    animationRes = R.raw.correct_create
                )
            } else if (title == "Pendientes con error" || title == "Sincronización offline parcial") {
                CreateUiFeedback.showErrorPopup(
                    activity = activity,
                    title = title,
                    details = message,
                    animationRes = R.raw.wrong
                )
            } else {
                UiNotifier.showBlocking(activity, title, message, iconRes)
            }
        }
    }

    private fun createForegroundInfo(): androidx.work.ForegroundInfo {
        val channelId = "offline_sync"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sincronizacion offline",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Sincronizando pendientes")
            .setContentText("Enviando cola offline en segundo plano...")
            .setOngoing(true)
            .build()
        return androidx.work.ForegroundInfo(1001, notification)
    }

    private fun shouldUseForeground(context: Context): Boolean {
        if (isEmulator()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk" == Build.PRODUCT
    }
}