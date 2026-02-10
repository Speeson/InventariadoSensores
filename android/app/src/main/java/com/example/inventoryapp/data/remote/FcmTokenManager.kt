package com.example.inventoryapp.data.remote

import android.content.Context
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.model.FcmTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FcmTokenManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sync(context: Context) {
        val token = SessionManager(context).getToken() ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
            sendToken(context, fcmToken)
        }
    }

    fun sendToken(context: Context, fcmToken: String) {
        if (fcmToken.isBlank()) return
        SessionManager(context).getToken() ?: return
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        scope.launch {
            runCatching {
                NetworkModule.api.registerFcmToken(
                    FcmTokenRequest(
                        token = fcmToken,
                        deviceId = deviceId,
                        platform = "android"
                    )
                )
            }
        }
    }
}
