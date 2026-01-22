package com.example.inventoryapp.data.remote.model

data class ProductListResponse(
    val items: List<ProductResponse>,
    val total: Int,
    val limit: Int,
    val offset: Int
)