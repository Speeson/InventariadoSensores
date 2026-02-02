package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

data class LocationResponseDto(
    val id: Int,
    val code: String,
    val description: String?,
    @SerializedName("created_at") val createdAt: String
)

data class LocationListResponseDto(
    val items: List<LocationResponseDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)
