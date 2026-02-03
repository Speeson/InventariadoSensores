package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

data class CategoryResponseDto(
    val id: Int,
    val name: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String?
)

data class CategoryListResponseDto(
    val items: List<CategoryResponseDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class CategoryCreateDto(
    val name: String
)

data class CategoryUpdateDto(
    val name: String? = null
)
