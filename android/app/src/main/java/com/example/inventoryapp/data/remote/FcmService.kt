package com.example.inventoryapp.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.inventoryapp.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenManager.sendToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: "Alerta"
        val body = message.notification?.body ?: "Tienes una nueva alerta."
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "alerts"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.setShowBadge(true)
            manager.createNotificationChannel(channel)
        }
        val prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val count = (prefs.getInt("badge_count", 0) + 1).coerceAtMost(99)
        prefs.edit().putInt("badge_count", count).apply()
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}
