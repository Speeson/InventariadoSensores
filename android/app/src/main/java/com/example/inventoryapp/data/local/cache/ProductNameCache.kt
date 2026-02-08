package com.example.inventoryapp.data.local.cache

data class ProductNameCache(
    val items: List<ProductNameItem>
)

data class ProductNameItem(
    val id: Int,
    val name: String
)
