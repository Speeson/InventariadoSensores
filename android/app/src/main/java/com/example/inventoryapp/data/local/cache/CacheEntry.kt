package com.example.inventoryapp.data.local.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_entries")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long
)
