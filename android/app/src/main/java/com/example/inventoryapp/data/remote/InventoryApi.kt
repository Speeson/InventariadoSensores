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
import okhttp3.ResponseBody
import okhttp3.MultipartBody
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventListResponseDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.data.remote.model.EventTypeDto
import com.example.inventoryapp.data.remote.model.CategoryCreateDto
import com.example.inventoryapp.data.remote.model.CategoryListResponseDto
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.data.remote.model.CategoryUpdateDto
import com.example.inventoryapp.data.remote.model.ThresholdCreateDto
import com.example.inventoryapp.data.remote.model.ThresholdListResponseDto
import com.example.inventoryapp.data.remote.model.ThresholdResponseDto
import com.example.inventoryapp.data.remote.model.ThresholdUpdateDto
import com.example.inventoryapp.data.remote.model.AlertListResponseDto
import com.example.inventoryapp.data.remote.model.AlertResponseDto
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.data.remote.model.FcmTokenRequest
import com.example.inventoryapp.data.remote.model.ImportSummaryResponseDto
import com.example.inventoryapp.data.remote.model.ImportReviewListResponseDto
import com.example.inventoryapp.data.remote.model.BasicOkDto
import retrofit2.http.Multipart
import retrofit2.http.Part




data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class UserMeResponse(
    val id: Int,
    val username: String,
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
    suspend fun health(): Response<HealthResponseDto>

    @POST("movements/in")
    suspend fun movementIn(@Body body: MovementOperationRequest): retrofit2.Response<MovementWithStockResponseDto>

    @POST("movements/out")
    suspend fun movementOut(@Body body: MovementOperationRequest): retrofit2.Response<MovementWithStockResponseDto>

    @POST("movements/adjust")
    suspend fun movementAdjust(@Body body: MovementAdjustOperationRequest): retrofit2.Response<MovementWithStockResponseDto>

    @POST("movements/transfer")
    suspend fun movementTransfer(@Body body: MovementTransferOperationRequest): retrofit2.Response<MovementTransferResponseDto>

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
        @Query("order_by") orderBy: String? = null,
        @Query("order_dir") orderDir: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ProductListResponseDto>

    @POST("products/")
    suspend fun createProduct(@Body body: ProductCreateDto): Response<ProductResponseDto>

    @GET("products/{product_id}")
    suspend fun getProduct(@Path("product_id") productId: Int): Response<ProductResponseDto>

    @GET("products/{product_id}/label.svg")
    suspend fun getProductLabelSvg(@Path("product_id") productId: Int): Response<ResponseBody>

    @POST("products/{product_id}/label/regenerate")
    suspend fun regenerateProductLabel(@Path("product_id") productId: Int): Response<ResponseBody>

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

    @GET("reports/turnover")
    suspend fun getTurnoverReport(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("location") location: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<TurnoverResponseDto>

    @GET("reports/top-consumed")
    suspend fun getTopConsumedReport(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("location") location: String? = null,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): Response<TopConsumedResponseDto>




    @GET("locations/")
    suspend fun listLocations(
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0
    ): Response<LocationListResponseDto>

    @GET("categories/")
    suspend fun listCategories(
        @Query("name") name: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<CategoryListResponseDto>

    @GET("categories/{category_id}")
    suspend fun getCategory(@Path("category_id") categoryId: Int): Response<CategoryResponseDto>

    @POST("categories/")
    suspend fun createCategory(@Body body: CategoryCreateDto): Response<CategoryResponseDto>

    @PATCH("categories/{category_id}")
    suspend fun updateCategory(
        @Path("category_id") categoryId: Int,
        @Body body: CategoryUpdateDto
    ): Response<CategoryResponseDto>

    @DELETE("categories/{category_id}")
    suspend fun deleteCategory(@Path("category_id") categoryId: Int): Response<Unit>

    @GET("thresholds/")
    suspend fun listThresholds(
        @Query("product_id") productId: Int? = null,
        @Query("location") location: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ThresholdListResponseDto>

    @GET("thresholds/{threshold_id}")
    suspend fun getThreshold(@Path("threshold_id") thresholdId: Int): Response<ThresholdResponseDto>

    @POST("thresholds/")
    suspend fun createThreshold(@Body body: ThresholdCreateDto): Response<ThresholdResponseDto>

    @PATCH("thresholds/{threshold_id}")
    suspend fun updateThreshold(
        @Path("threshold_id") thresholdId: Int,
        @Body body: ThresholdUpdateDto
    ): Response<ThresholdResponseDto>

    @DELETE("thresholds/{threshold_id}")
    suspend fun deleteThreshold(@Path("threshold_id") thresholdId: Int): Response<Unit>

    @GET("alerts/")
    suspend fun listAlerts(
        @Query("status") status: AlertStatusDto? = null,
        @Query("product_id") productId: Int? = null,
        @Query("location") location: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<AlertListResponseDto>

    @POST("alerts/{alert_id}/ack")
    suspend fun ackAlert(@Path("alert_id") alertId: Int): Response<AlertResponseDto>

    @POST("users/fcm-token")
    suspend fun registerFcmToken(@Body payload: FcmTokenRequest): Response<Unit>

    @Multipart
    @POST("imports/events/csv")
    suspend fun importEventsCsv(
        @Part file: MultipartBody.Part,
        @Query("dry_run") dryRun: Boolean = true,
        @Query("fuzzy_threshold") fuzzyThreshold: Double = 0.9
    ): Response<ImportSummaryResponseDto>

    @Multipart
    @POST("imports/transfers/csv")
    suspend fun importTransfersCsv(
        @Part file: MultipartBody.Part,
        @Query("dry_run") dryRun: Boolean = true,
        @Query("fuzzy_threshold") fuzzyThreshold: Double = 0.9
    ): Response<ImportSummaryResponseDto>

    @GET("imports/reviews")
    suspend fun listImportReviews(
        @Query("batch_id") batchId: Int? = null,
        @Query("kind") kind: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ImportReviewListResponseDto>

    @POST("imports/reviews/{review_id}/approve")
    suspend fun approveImportReview(
        @Path("review_id") reviewId: Int
    ): Response<BasicOkDto>

    @POST("imports/reviews/{review_id}/reject")
    suspend fun rejectImportReview(
        @Path("review_id") reviewId: Int
    ): Response<BasicOkDto>
}



