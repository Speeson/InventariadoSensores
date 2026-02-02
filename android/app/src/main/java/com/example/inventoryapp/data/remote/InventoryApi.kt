package com.example.inventoryapp.data.remote

import com.example.inventoryapp.data.remote.model.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.GET
import com.example.inventoryapp.data.remote.model.*
import com.example.inventoryapp.data.remote.model.ProductCreateDto
import com.example.inventoryapp.data.remote.model.ProductListResponseDto
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.data.remote.model.ProductUpdateDto
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventListResponseDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.data.remote.model.EventTypeDto




data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
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
        @Field("email") email: String,
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

    @POST("movements/in")
    suspend fun movementIn(@Body body: MovementOperationRequest): retrofit2.Response<MovementWithStockResponseDto>

    @POST("movements/out")
    suspend fun movementOut(@Body body: MovementOperationRequest): retrofit2.Response<MovementWithStockResponseDto>

    @POST("movements/adjust")
    suspend fun movementAdjust(@Body body: MovementAdjustOperationRequest): retrofit2.Response<MovementWithStockResponseDto>

    @GET("movements/")
    suspend fun listMovements(
        @Query("product_id") productId: Int? = null,
        @Query("movement_type") movementType: MovementTypeDto? = null,
        @Query("movement_source") movementSource: MovementSourceDto? = null,
        @Query("user_id") userId: Int? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0
    ): retrofit2.Response<MovementListResponseDto>

    @GET("products/")
    suspend fun listProducts(
        @Query("sku") sku: String? = null,
        @Query("name") name: String? = null,
        @Query("barcode") barcode: String? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("active") active: Boolean? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ProductListResponseDto>

    @POST("products/")
    suspend fun createProduct(@Body body: ProductCreateDto): Response<ProductResponseDto>

    @GET("products/{product_id}")
    suspend fun getProduct(@Path("product_id") productId: Int): Response<ProductResponseDto>

    @PATCH("products/{product_id}")
    suspend fun updateProduct(
        @Path("product_id") productId: Int,
        @Body body: ProductUpdateDto
    ): Response<ProductResponseDto>

    @DELETE("products/{product_id}")
    suspend fun deleteProduct(@Path("product_id") productId: Int): Response<Unit>

    @GET("stocks/")
    suspend fun listStocks(
        @Query("product_id") productId: Int? = null,
        @Query("location") location: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<StockListResponseDto>

    @POST("stocks/")
    suspend fun createStock(@Body body: StockCreateDto): Response<StockResponseDto>

    @GET("stocks/{stock_id}")
    suspend fun getStock(@Path("stock_id") stockId: Int): Response<StockResponseDto>

    @PATCH("stocks/{stock_id}")
    suspend fun updateStock(
        @Path("stock_id") stockId: Int,
        @Body body: StockUpdateDto
    ): Response<StockResponseDto>

    @GET("events/")
    suspend fun listEvents(
        @Query("event_type") eventType: EventTypeDto? = null,
        @Query("product_id") productId: Int? = null,
        @Query("processed") processed: Boolean? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): retrofit2.Response<EventListResponseDto>

    @POST("events/")
    suspend fun createEvent(@Body body: EventCreateDto): retrofit2.Response<EventResponseDto>



}



