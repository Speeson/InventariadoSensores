package com.example.inventoryapp.data.remote

import android.content.Context
import com.example.inventoryapp.data.local.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {

    private const val BASE_URL = "http://10.0.2.2:8000/" // emulador

    private lateinit var session: SessionManager
    lateinit var api: InventoryApi
        private set

    fun init(context: Context) {
        session = SessionManager(context.applicationContext)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(logging)
            .build()

        api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(InventoryApi::class.java)
    }
}
