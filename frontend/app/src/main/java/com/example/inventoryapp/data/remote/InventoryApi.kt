package com.example.inventoryapp.data.remote

import com.example.inventoryapp.data.remote.model.TokenResponse
import com.example.inventoryapp.data.remote.model.ProductListResponse
import com.example.inventoryapp.data.remote.model.EventCreateRequest
import com.example.inventoryapp.data.remote.model.EventResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
interface InventoryApi {

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<TokenResponse>

    // ✅ 1) Buscar productos (soporta filtro por barcode)
    @GET("products")
    suspend fun listProducts(
        @Query("barcode") barcode: String? = null,
        @Query("limit") limit: Int = 1,
        @Query("offset") offset: Int = 0
    ): Response<ProductListResponse>

    // ✅ 2) Crear evento (esto registra en BD y en el back actualiza stock)
    @POST("events")
    suspend fun createEvent(
        @Body payload: EventCreateRequest
    ): Response<EventResponse>
}