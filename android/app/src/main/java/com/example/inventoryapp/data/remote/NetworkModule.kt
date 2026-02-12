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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.inventoryapp.data.local.SystemAlertType
import com.example.inventoryapp.ui.common.SystemAlertManager
import com.example.inventoryapp.ui.common.ActivityTracker
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.R
import android.content.Intent

object NetworkModule {
    const val EXTRA_AUTH_EXPIRED_NOTIFIED = "auth_expired_notified"

    private const val PREFS_NAME = "network_prefs"
    private const val KEY_CUSTOM_HOST = "custom_host"
    private const val KEY_MANUAL_OFFLINE = "manual_offline"
    private const val HEALTH_PING_MS = 5_000L

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

    private fun resolveHost(): String {
        return if (isEmulator()) {
            "10.0.2.2"
        } else {
            getCustomHost()?.ifBlank { null } ?: BuildConfig.LOCAL_DEV_HOST
        }
    }

    private fun baseUrl(): String {
        val host = resolveHost()
        return "http://$host:8000/"
    }

    fun buildWsUrl(path: String): String {
        val host = resolveHost()
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "ws://$host:8000$normalized"
    }

    private lateinit var appContext: Context
    private val healthScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var healthPingStarted = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun startHealthPing() {
        if (healthPingStarted) return
        healthPingStarted = true
        healthScope.launch {
            while (isActive) {
                delay(HEALTH_PING_MS)
                pingHealthOnce()
            }
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    @Volatile private var networkDown = false
    @Volatile private var lastProbeAt: Long = 0L
    private const val OFFLINE_PROBE_MS = 10_000L
    private val authRedirectInProgress = AtomicBoolean(false)
    private val _offlineState = MutableStateFlow(false)
    val offlineState: StateFlow<Boolean> = _offlineState.asStateFlow()

    private fun notifyNetworkDownOnce() {
        if (networkDown) return
        networkDown = true
        _offlineState.value = true
        val activity = ActivityTracker.getCurrent()
        if (activity != null) {
            activity.runOnUiThread {
                if (shouldShowOfflineNotice(activity)) {
                    UiNotifier.showBlockingTimed(
                        activity,
                        "Sin conexión. Trabajando en modo offline.",
                        R.drawable.offline
                    )
                }
            }
        }
    }

    private fun notifyNetworkUpOnce() {
        if (!networkDown) return
        networkDown = false
        _offlineState.value = false
        val activity = ActivityTracker.getCurrent()
        if (activity != null) {
            activity.runOnUiThread {
                UiNotifier.showBlockingTimed(
                    activity,
                    "Conexión restablecida.",
                    R.drawable.online
                )
            }
        }
    }

    fun forceOnline() {
        if (isManualOffline()) {
            notifyNetworkDownOnce()
            return
        }
        networkDown = false
        lastProbeAt = 0L
        _offlineState.value = false
    }

    fun resetAuthRedirectGuard() {
        authRedirectInProgress.set(false)
    }

    fun isManualOffline(): Boolean {
        if (!::appContext.isInitialized) return false
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MANUAL_OFFLINE, false)
    }

    fun setManualOffline(enabled: Boolean) {
        if (!::appContext.isInitialized) return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MANUAL_OFFLINE, enabled).apply()
        if (enabled) {
            notifyNetworkDownOnce()
        } else {
            lastProbeAt = 0L
            notifyNetworkUpOnce()
        }
    }

    fun toggleManualOffline(): Boolean {
        val next = !isManualOffline()
        setManualOffline(next)
        return next
    }

    private suspend fun pingHealthOnce() {
        if (!::appContext.isInitialized) return
        if (isManualOffline()) {
            notifyNetworkDownOnce()
            return
        }
        val session = SessionManager(appContext)
        val token = session.getToken()
        if (token.isNullOrBlank() || session.isTokenExpired(token)) return
        val prefs = appContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val role = prefs.getString("cached_role", null)
        val userId = prefs.getInt("cached_user_id", -1)
        if (role.isNullOrBlank() || userId <= 0) return
        try {
            val res = api.health()
            if (res.isSuccessful) {
                notifyNetworkUpOnce()
            } else {
                notifyNetworkDownOnce()
            }
        } catch (_: Exception) {
            notifyNetworkDownOnce()
        }
    }

    private fun shouldShowOfflineNotice(activity: android.app.Activity): Boolean {
        if (activity is LoginActivity) return false
        val session = SessionManager(appContext)
        val token = session.getToken()
        if (token.isNullOrBlank()) return false
        if (session.isTokenExpired(token)) return false
        val prefs = appContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val role = prefs.getString("cached_role", null)
        val userId = prefs.getInt("cached_user_id", -1)
        return !role.isNullOrBlank() && userId > 0
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                if (isManualOffline()) {
                    notifyNetworkDownOnce()
                    throw IOException("Manual offline mode")
                }
                if (networkDown) {
                    val now = System.currentTimeMillis()
                    val allowProbe = now - lastProbeAt >= OFFLINE_PROBE_MS
                    if (!allowProbe) {
                        throw IOException("Offline mode")
                    }
                    lastProbeAt = now
                }
                val session = SessionManager(appContext)
                val token = session.getToken()

                val req = if (!token.isNullOrBlank()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else chain.request()

                try {
                    val response = chain.proceed(req)
                    notifyNetworkUpOnce()
                    if (response.code >= 500) {
                        val path = response.request.url.encodedPath
                        val userMessage = buildFriendlyServerError(path)
                        val message = if (isAdminUser()) {
                            "Error ${response.code} en $path. Revisa logs del backend."
                        } else {
                            userMessage
                        }
                        SystemAlertManager.record(
                            appContext,
                            SystemAlertType.SERVER_ERROR,
                            "Servicio no disponible",
                            message
                        )
                    } else if (response.code == 401) {
                        val path = response.request.url.encodedPath
                        if (path.startsWith("/auth/")) {
                            return@addInterceptor response
                        }
                        if (!authRedirectInProgress.compareAndSet(false, true)) {
                            return@addInterceptor response
                        }
                        val session = SessionManager(appContext)
                        val token = session.getToken()
                        val expired = token.isNullOrBlank() || session.isTokenExpired(token)
                        if (!expired) {
                            // Backend no disponible o no valida sesión: conservar token y permitir modo offline
                            authRedirectInProgress.set(false)
                            response.close()
                            notifyNetworkDownOnce()
                            throw IOException("Auth check unavailable (offline)")
                        }
                        SystemAlertManager.record(
                            appContext,
                            SystemAlertType.AUTH_EXPIRED,
                            "Sesión caducada",
                            "Sesión caducada. Inicia sesión.",
                            blocking = false
                        )
                        session.clearToken()
                        val activity = ActivityTracker.getCurrent()
                        if (activity != null) {
                            activity.runOnUiThread {
                                val i = Intent(activity, LoginActivity::class.java)
                                i.putExtra(EXTRA_AUTH_EXPIRED_NOTIFIED, true)
                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                activity.startActivity(i)
                                activity.finish()
                            }
                        }
                    }
                    response
                } catch (e: IOException) {
                    notifyNetworkDownOnce()
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

    private fun isAdminUser(): Boolean {
        val prefs = appContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val role = prefs.getString("cached_role", null) ?: return false
        return role.equals("ADMIN", ignoreCase = true)
    }

    private fun buildFriendlyServerError(path: String): String {
        return when {
            path.startsWith("/stocks/") -> "No se pudo cargar el stock. Intenta de nuevo."
            path.startsWith("/products/") -> "No se pudo cargar el producto. Intenta de nuevo."
            path.startsWith("/imports/") -> "No se pudo procesar la importación. Revisa el CSV e inténtalo de nuevo."
            path.startsWith("/events/") -> "No se pudo cargar eventos. Intenta de nuevo."
            path.startsWith("/movements/") -> "No se pudo cargar movimientos. Intenta de nuevo."
            path.startsWith("/alerts/") -> "No se pudieron cargar las alertas. Intenta de nuevo."
            path.startsWith("/reports/") -> "No se pudo cargar el reporte. Intenta de nuevo."
            else -> "Ha ocurrido un problema temporal en el servidor. Intenta de nuevo en unos minutos."
        }
    }
}
