package com.example.inventoryapp.data.local.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CacheEntry::class], version = 1, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile private var INSTANCE: CacheDatabase? = null

        fun getInstance(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "app_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
