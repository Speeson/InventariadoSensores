package com.example.inventoryapp.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class PendingType {
    EVENT_CREATE,
    MOVEMENT_IN,
    MOVEMENT_OUT,
    MOVEMENT_ADJUST,
    PRODUCT_CREATE,
    PRODUCT_UPDATE,
    PRODUCT_DELETE,
    STOCK_CREATE,
    STOCK_UPDATE
}

data class PendingRequest(
    val type: PendingType,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

class OfflineQueue(context: Context) {

    private val prefs = context.getSharedPreferences("offline_queue", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val key = "pending_items"

    fun enqueue(type: PendingType, payloadJson: String) {
        val all = getAll().toMutableList()
        all.add(PendingRequest(type, payloadJson))
        save(all)
    }

    fun getAll(): List<PendingRequest> {
        val json = prefs.getString(key, "[]") ?: "[]"
        val t = object : TypeToken<List<PendingRequest>>() {}.type
        return runCatching { gson.fromJson<List<PendingRequest>>(json, t) }.getOrDefault(emptyList())
    }

    fun size(): Int = getAll().size

    fun removeFirst(n: Int) {
        if (n <= 0) return
        val all = getAll()
        val remaining = if (n >= all.size) emptyList() else all.drop(n)
        save(remaining)
    }

    fun clear() {
        prefs.edit().remove(key).apply()
    }

    private fun save(items: List<PendingRequest>) {
        prefs.edit().putString(key, gson.toJson(items)).apply()
    }
}
