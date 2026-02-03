package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

data class ThresholdResponseDto(
    val id: Int,
    @SerializedName("product_id") val productId: Int,
    @SerializedName("location_id") val locationId: Int? = null,
    val location: String? = null,
    @SerializedName("min_quantity") val minQuantity: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String?
)

data class ThresholdListResponseDto(
    val items: List<ThresholdResponseDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class ThresholdCreateDto(
    @SerializedName("product_id") val productId: Int,
    val location: String? = null,
    @SerializedName("min_quantity") val minQuantity: Int
)

data class ThresholdUpdateDto(
    val location: String? = null,
    @SerializedName("min_quantity") val minQuantity: Int? = null
)
