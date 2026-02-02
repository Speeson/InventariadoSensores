package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

data class ProductResponseDto(
    val sku: String,
    val name: String,
    val barcode: String?,
    @SerializedName("category_id") val categoryId: Int,
    val active: Boolean = true,
    val id: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class ProductListResponseDto(
    val items: List<ProductResponseDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class ProductCreateDto(
    val sku: String,
    val name: String,
    val barcode: String,
    @SerializedName("category_id") val categoryId: Int,
    val active: Boolean? = true
)

data class ProductUpdateDto(
    val name: String? = null,
    val barcode: String? = null,
    @SerializedName("category_id") val categoryId: Int? = null,
    val active: Boolean? = null
)
