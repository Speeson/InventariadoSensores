package com.example.inventoryapp.data.remote.model

data class ProductResponse(
    val id: Int,
    val sku: String,
    val name: String,
    val barcode: String?,
    val category_id: Int,
    val active: Boolean?
)
