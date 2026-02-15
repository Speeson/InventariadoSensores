package com.example.inventoryapp.data.remote.model

data class AuditLogResponseDto(
    val id: Int,
    val entity: String,
    val action: String,
    val user_id: Int,
    val details: String?,
    val created_at: String
)

data class AuditLogListResponseDto(
    val items: List<AuditLogResponseDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)
