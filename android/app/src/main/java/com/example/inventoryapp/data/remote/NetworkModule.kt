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
import com.example.inventoryapp.ui.auth.LoginActivity
import android.content.Intent

object NetworkModule {

    private const val PREFS_NAME = "network_prefs"
    private const val KEY_CUSTOM_HOST = "custom_host"

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
        val host = if (isEmulator()) {
            "10.0.2.2"
        } else {
            getCustomHost()?.ifBlank { null } ?: BuildConfig.LOCAL_DEV_HOST
        }
        return "http://$host:8000/"
    }

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    @Volatile private var lastNetworkPopupAt: Long = 0L

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
                        SessionManager(appContext).clearToken()
                        val activity = ActivityTracker.getCurrent()
                        if (activity != null) {
                            activity.runOnUiThread {
                                val i = Intent(activity, LoginActivity::class.java)
                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                activity.startActivity(i)
                                activity.finish()
                            }
                        }
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
                        val now = System.currentTimeMillis()
                        if (now - lastNetworkPopupAt > 30_000L) {
                            lastNetworkPopupAt = now
                            SystemAlertManager.record(
                                appContext,
                                SystemAlertType.NETWORK,
                                "Sin conexión",
                                "Se perdió la conexión. Reconectando...",
                                blocking = true
                            )
                        }
                    }
                    throw e
                }
            }
            .build()
    }

    @Volatile private var apiInstance: InventoryApi? = null
    @Volatile private var cachedBaseUrl: String? = null

    val api: InventoryApi
        get() {
            val url = baseUrl()
            if (apiInstance == null || cachedBaseUrl != url) {
                cachedBaseUrl = url
                apiInstance = Retrofit.Builder()
                    .baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                    .create(InventoryApi::class.java)
            }
            return apiInstance!!
        }

    fun setCustomHost(host: String?) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_HOST, host?.trim()).apply()
        cachedBaseUrl = null
        apiInstance = null
    }

    fun getCustomHost(): String? {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_HOST, null)
    }
}
