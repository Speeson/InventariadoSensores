package com.example.inventoryapp.data.repository.remote

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventListResponseDto
import com.example.inventoryapp.data.remote.model.EventResponseDto

class EventRepository {

    suspend fun listEvents(limit: Int = 50, offset: Int = 0): Result<EventListResponseDto> {
        return try {
            val res = NetworkModule.api.listEvents(limit = limit, offset = offset)
            if (!res.isSuccessful || res.body() == null) {
                Result.failure(Exception("HTTP ${res.code()}: ${res.errorBody()?.string() ?: "sin detalle"}"))
            } else {
                Result.success(res.body()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createEvent(dto: EventCreateDto): Result<EventResponseDto> {
        return try {
            val res = NetworkModule.api.createEvent(dto)
            if (!res.isSuccessful || res.body() == null) {
                Result.failure(Exception("HTTP ${res.code()}: ${res.errorBody()?.string() ?: "sin detalle"}"))
            } else {
                Result.success(res.body()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
