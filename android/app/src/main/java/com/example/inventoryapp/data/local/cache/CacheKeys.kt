package com.example.inventoryapp.data.local.cache

import java.util.TreeMap

object CacheKeys {
    fun list(prefix: String, params: Map<String, Any?>): String {
        val sorted = TreeMap<String, Any?>()
        sorted.putAll(params)
        val encoded = sorted.entries.joinToString("&") { (k, v) ->
            val value = v?.toString() ?: ""
            "$k=$value"
        }
        return "$prefix:list?$encoded"
    }

    fun detail(prefix: String, id: Any): String = "$prefix:detail:$id"
}
