package com.example.inventoryapp.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache_entries WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CacheEntry)

    @Query("DELETE FROM cache_entries WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM cache_entries WHERE `key` NOT IN (SELECT `key` FROM cache_entries ORDER BY updatedAt DESC LIMIT :limit)")
    suspend fun prune(limit: Int)
}
