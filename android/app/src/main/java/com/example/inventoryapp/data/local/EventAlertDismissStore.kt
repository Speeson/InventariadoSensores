package com.example.inventoryapp.data.local

import android.content.Context

class EventAlertDismissStore(context: Context) {
    private val prefs = context.getSharedPreferences("event_alert_dismissed", Context.MODE_PRIVATE)
    private val key = "dismissed_ids"

    fun list(): Set<Int> {
        val raw = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun addAll(ids: List<Int>) {
        val all = list().toMutableSet()
        all.addAll(ids)
        save(all)
    }

    fun clear() {
        prefs.edit().remove(key).apply()
    }

    private fun save(ids: Set<Int>) {
        prefs.edit().putStringSet(key, ids.map { it.toString() }.toSet()).apply()
    }
}
