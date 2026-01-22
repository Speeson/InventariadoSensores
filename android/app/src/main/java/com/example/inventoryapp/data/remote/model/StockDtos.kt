package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

data class StockResponseDto(
    @SerializedName("product_id") val productId: Int,
    val location: String,
    val quantity: Int,
    val id: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class StockListResponseDto(
    val items: List<StockResponseDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class StockCreateDto(
    @SerializedName("product_id") val productId: Int,
    val location: String,
    val quantity: Int
)

data class StockUpdateDto(
    val location: String? = null,
    val quantity: Int? = null
)
