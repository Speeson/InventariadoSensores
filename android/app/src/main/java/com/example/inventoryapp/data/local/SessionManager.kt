package com.example.inventoryapp.data.local

import android.content.Context
import com.example.inventoryapp.data.remote.AlertsWebSocketManager

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString("token", token).apply()
    }

    fun getToken(): String? = prefs.getString("token", null)

    fun clear() {
        prefs.edit().clear().apply()
        AlertsWebSocketManager.disconnect()
    }
    fun clearToken() {
        prefs.edit().remove("token").apply()
        AlertsWebSocketManager.disconnect()
    }

}
