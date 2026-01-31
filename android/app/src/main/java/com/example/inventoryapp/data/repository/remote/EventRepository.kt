package com.example.inventoryapp.data.repository.remote

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventListResponseDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.data.remote.model.EventTypeDto
import retrofit2.Response

/**
 * ✅ TC-104: Repository para centralizar llamadas de eventos
 */
class EventRepository {

    suspend fun listEvents(
        eventType: EventTypeDto? = null,
        productId: Int? = null,
        processed: Boolean? = null,
        limit: Int = 50,
        offset: Int = 0
    ): Response<EventListResponseDto> {
        return NetworkModule.api.listEvents(
            eventType = eventType,
            productId = productId,
            processed = processed,
            limit = limit,
            offset = offset
        )
    }

    suspend fun createEvent(body: EventCreateDto): Response<EventResponseDto> {
        return NetworkModule.api.createEvent(body)
    }
}
