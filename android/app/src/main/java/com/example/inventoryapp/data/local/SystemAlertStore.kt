package com.example.inventoryapp.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class SystemAlertType {
    API_DOWN,
    NETWORK,
    TIMEOUT,
    AUTH_EXPIRED,
    SERVER_ERROR,
    OFFLINE_SYNC_OK,
    UNKNOWN
}

data class SystemAlert(
    val id: String,
    val type: SystemAlertType,
    val title: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val seen: Boolean = false
)

class SystemAlertStore(context: Context) {

    private val prefs = context.getSharedPreferences("system_alerts", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "alerts"
    private val lastKey = "last_alert"

    fun list(): List<SystemAlert> {
        val json = prefs.getString(key, "[]") ?: "[]"
        val t = object : TypeToken<List<SystemAlert>>() {}.type
        return runCatching { gson.fromJson<List<SystemAlert>>(json, t) }.getOrDefault(emptyList())
    }

    fun add(alert: SystemAlert) {
        val all = list().toMutableList()
        all.add(0, alert)
        save(all)
    }

    fun markSeen(id: String) {
        val all = list().map { if (it.id == id) it.copy(seen = true) else it }
        save(all)
    }

    fun remove(id: String) {
        val all = list().filterNot { it.id == id }
        save(all)
    }

    fun clearAll() {
        save(emptyList())
    }

    fun latestUnseen(): SystemAlert? = list().firstOrNull { !it.seen }

    fun shouldRecord(type: SystemAlertType, message: String, cooldownMs: Long = 30_000L): Boolean {
        val last = prefs.getString(lastKey, null) ?: return true
        val parts = last.split("|", limit = 3)
        if (parts.size < 3) return true
        val lastType = parts[0]
        val lastMsg = parts[1]
        val lastAt = parts[2].toLongOrNull() ?: return true
        val now = System.currentTimeMillis()
        return !(lastType == type.name && lastMsg == message && (now - lastAt) < cooldownMs)
    }

    fun rememberLast(type: SystemAlertType, message: String) {
        val value = "${type.name}|$message|${System.currentTimeMillis()}"
        prefs.edit().putString(lastKey, value).apply()
    }

    private fun save(items: List<SystemAlert>) {
        prefs.edit().putString(key, gson.toJson(items)).apply()
    }
}
