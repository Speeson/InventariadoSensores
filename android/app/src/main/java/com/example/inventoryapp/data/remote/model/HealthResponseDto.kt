package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

data class HealthResponseDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("checks") val checks: Map<String, Any?>? = null
)

