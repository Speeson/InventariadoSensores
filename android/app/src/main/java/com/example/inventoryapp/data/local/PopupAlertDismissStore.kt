package com.example.inventoryapp.data.local

import android.content.Context

class PopupAlertDismissStore(context: Context) {
    private val prefs = context.getSharedPreferences("popup_alert_dismissed", Context.MODE_PRIVATE)
    private val key = "dismissed_ids"

    fun list(): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()

    fun add(id: String) {
        val all = list().toMutableSet()
        all.add(id)
        save(all)
    }

    fun addAll(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val all = list().toMutableSet()
        all.addAll(ids)
        save(all)
    }

    fun clear() {
        prefs.edit().remove(key).apply()
    }

    private fun save(ids: Set<String>) {
        prefs.edit().putStringSet(key, ids).apply()
    }
}
