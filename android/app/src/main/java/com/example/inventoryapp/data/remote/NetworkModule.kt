package com.example.inventoryapp.data.remote

import android.content.Context
import android.os.Build
import com.example.inventoryapp.BuildConfig
import com.example.inventoryapp.data.local.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import com.example.inventoryapp.data.local.SystemAlertType
import com.example.inventoryapp.data.local.SystemAlertStore
import com.example.inventoryapp.ui.common.SystemAlertManager
import com.example.inventoryapp.ui.common.ActivityTracker
import com.example.inventoryapp.ui.common.UiNotifier

object NetworkModule {

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk" == Build.PRODUCT
    }

    private fun baseUrl(): String {
        val host = if (isEmulator()) "10.0.2.2" else BuildConfig.LOCAL_DEV_HOST
        return "http://$host:8000/"
    }
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val session = SessionManager(appContext)
                val token = session.getToken()

                val req = if (!token.isNullOrBlank()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else chain.request()

                try {
                    val response = chain.proceed(req)
                    if (response.code >= 500) {
                        SystemAlertManager.record(
                            appContext,
                            SystemAlertType.SERVER_ERROR,
                            "Servicio no disponible",
                            "El servidor respondió ${response.code}. Revisa API/DB/Redis/Celery."
                        )
                    } else if (response.code == 401) {
                        SystemAlertManager.record(
                            appContext,
                            SystemAlertType.AUTH_EXPIRED,
                            "Sesión caducada",
                            "Tu sesión ha expirado. Inicia sesión de nuevo.",
                            blocking = false
                        )
                    }
                    response
                } catch (e: IOException) {
                    if (e is SocketTimeoutException) {
                        val store = SystemAlertStore(appContext)
                        if (store.shouldRecord(SystemAlertType.TIMEOUT, "timeout", 30_000L)) {
                            store.rememberLast(SystemAlertType.TIMEOUT, "timeout")
                            val activity = ActivityTracker.getCurrent()
                            if (activity != null) {
                                activity.runOnUiThread {
                                    UiNotifier.show(activity, "Timeout de conexión. Intenta de nuevo.")
                                }
                            }
                        }
                    } else {
                        val msg = e.message ?: "No se pudo contactar con el servidor"
                        SystemAlertManager.record(appContext, SystemAlertType.NETWORK, "Sin conexión", msg)
                    }
                    throw e
                }
            }
            .build()
    }

    val api: InventoryApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl())
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(InventoryApi::class.java)
    }
}
