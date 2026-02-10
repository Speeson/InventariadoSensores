package com.example.inventoryapp.data.local

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.inventoryapp.ui.common.SystemAlertManager
import com.example.inventoryapp.data.local.SystemAlertType
import com.example.inventoryapp.R

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (shouldUseForeground(applicationContext)) {
            setForeground(createForegroundInfo())
        }
        val report = OfflineSyncer.flush(applicationContext)
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
                "Sincronización offline",
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

    private fun createForegroundInfo(): androidx.work.ForegroundInfo {
        val channelId = "offline_sync"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sincronización offline",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Sincronizando pendientes")
            .setContentText("Enviando cola offline en segundo plano…")
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
