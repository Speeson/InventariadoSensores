package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

data class FcmTokenRequest(
    val token: String,
    @SerializedName("device_id") val deviceId: String?,
    val platform: String = "android"
)
