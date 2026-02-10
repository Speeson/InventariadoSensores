package com.example.inventoryapp.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class PendingType {
    EVENT_CREATE,
    SCAN_EVENT,
    MOVEMENT_IN,
    MOVEMENT_OUT,
    MOVEMENT_ADJUST,
    MOVEMENT_TRANSFER,
    PRODUCT_CREATE,
    PRODUCT_UPDATE,
    PRODUCT_DELETE,
    CATEGORY_CREATE,
    THRESHOLD_CREATE,
    STOCK_CREATE,
    STOCK_UPDATE
}

data class PendingRequest(
    val type: PendingType,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class FailedRequest(
    val original: PendingRequest,
    val httpCode: Int? = null,
    val errorMessage: String,
    val failedAt: Long = System.currentTimeMillis()
)

class OfflineQueue(context: Context) {

    private val prefs = context.getSharedPreferences("offline_queue", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val appContext = context.applicationContext

    private val pendingKey = "pending_items"
    private val failedKey = "failed_items"

    fun enqueue(type: PendingType, payloadJson: String) {
        val all = getAll().toMutableList()
        all.add(PendingRequest(type, payloadJson))
        savePending(all)
        OfflineSyncScheduler.scheduleOneTime(appContext)
    }

    fun getAll(): List<PendingRequest> {
        val json = prefs.getString(pendingKey, "[]") ?: "[]"
        val t = object : TypeToken<List<PendingRequest>>() {}.type
        return runCatching { gson.fromJson<List<PendingRequest>>(json, t) }.getOrDefault(emptyList())
    }

    fun size(): Int = getAll().size

    fun saveAll(items: List<PendingRequest>) {
        savePending(items)
    }

    fun clear() {
        prefs.edit().remove(pendingKey).apply()
    }

    // ---- Failed queue (dead-letter) ----

    fun addFailed(f: FailedRequest) {
        val all = getFailed().toMutableList()
        all.add(f)
        saveFailed(all)
    }

    fun getFailed(): List<FailedRequest> {
        val json = prefs.getString(failedKey, "[]") ?: "[]"
        val t = object : TypeToken<List<FailedRequest>>() {}.type
        return runCatching { gson.fromJson<List<FailedRequest>>(json, t) }.getOrDefault(emptyList())
    }

    fun clearFailed() {
        prefs.edit().remove(failedKey).apply()
    }

    fun failedSize(): Int = getFailed().size

    fun removeFailedAt(index: Int) {
        val all = getFailed().toMutableList()
        if (index !in all.indices) return
        all.removeAt(index)
        saveFailed(all)
    }

    fun moveFailedBackToPending(index: Int) {
        val all = getFailed().toMutableList()
        if (index !in all.indices) return
        val item = all[index]

        // Re-enqueue el request original
        enqueue(item.original.type, item.original.payloadJson)

        // Lo quitamos de failed
        all.removeAt(index)
        saveFailed(all)
    }


    private fun savePending(items: List<PendingRequest>) {
        prefs.edit().putString(pendingKey, gson.toJson(items)).apply()
    }

    private fun saveFailed(items: List<FailedRequest>) {
        prefs.edit().putString(failedKey, gson.toJson(items)).apply()
    }
}
