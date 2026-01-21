package com.example.inventoryapp.data.remote

import com.example.inventoryapp.data.remote.model.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.GET


data class RegisterRequest(
    val email: String,
    val password: String,
    val role: String? = null
)

data class UserMeResponse(
    val id: Int,
    val email: String,
    val role: String
)




interface InventoryApi {

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<TokenResponse>

    @POST("auth/register")
    suspend fun register(
        @Body body: RegisterRequest
    ): Response<TokenResponse>

    @GET("users/me")
    suspend fun me(): Response<UserMeResponse>

    @GET("health")
    suspend fun health(): Response<Unit>
}


