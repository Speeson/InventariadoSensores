package com.example.inventoryapp.data.local.cache

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CacheStore private constructor(context: Context) {
    private val dao = CacheDatabase.getInstance(context).cacheDao()
    private val gson = Gson()

    suspend fun <T> get(key: String, clazz: Class<T>): T? = withContext(Dispatchers.IO) {
        val entry = dao.get(key) ?: return@withContext null
        return@withContext runCatching { gson.fromJson(entry.json, clazz) }.getOrNull()
    }

    suspend fun put(key: String, value: Any, maxEntries: Int = 500) = withContext(Dispatchers.IO) {
        val json = gson.toJson(value)
        dao.upsert(CacheEntry(key = key, json = json, updatedAt = System.currentTimeMillis()))
        dao.prune(maxEntries)
    }

    suspend fun invalidatePrefix(prefix: String) = withContext(Dispatchers.IO) {
        dao.deleteByPrefix(prefix)
    }

    companion object {
        @Volatile private var INSTANCE: CacheStore? = null

        fun getInstance(context: Context): CacheStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CacheStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
