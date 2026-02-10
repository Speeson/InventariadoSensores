package com.example.inventoryapp.data.local

import android.content.Context
import android.util.Base64
import com.example.inventoryapp.data.remote.AlertsWebSocketManager
import org.json.JSONObject

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString("token", token).apply()
    }

    fun getToken(): String? = prefs.getString("token", null)

    fun isTokenExpired(token: String? = getToken()): Boolean {
        if (token.isNullOrBlank()) return true
        return try {
            val payloadJson = decodeJwtPayload(token) ?: return true
            val payload = JSONObject(payloadJson)
            val exp = payload.optLong("exp", 0L)
            if (exp <= 0L) return true
            val nowSeconds = System.currentTimeMillis() / 1000L
            exp <= nowSeconds
        } catch (_: Exception) {
            true
        }
    }

    private fun decodeJwtPayload(token: String): String? {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
        val padded = when (payload.length % 4) {
            2 -> payload + "=="
            3 -> payload + "="
            else -> payload
        }
        val decoded = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        return String(decoded, Charsets.UTF_8)
    }

    fun clear() {
        prefs.edit().clear().apply()
        AlertsWebSocketManager.disconnect()
    }
    fun clearToken() {
        prefs.edit().remove("token").apply()
        AlertsWebSocketManager.disconnect()
    }

}
