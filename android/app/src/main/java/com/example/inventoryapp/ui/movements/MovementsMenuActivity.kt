package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.cache.CacheKeys
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.local.cache.ProductNameCache
import com.example.inventoryapp.data.local.cache.ProductNameItem
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementAdjustOperationRequest
import com.example.inventoryapp.data.remote.model.MovementOperationRequest
import com.example.inventoryapp.data.remote.model.MovementListResponseDto
import com.example.inventoryapp.data.remote.model.MovementResponseDto
import com.example.inventoryapp.data.remote.model.MovementSourceDto
import com.example.inventoryapp.data.remote.model.MovementTransferOperationRequest
import com.example.inventoryapp.data.remote.model.MovementTypeDto
import com.example.inventoryapp.databinding.ActivityMovementsMenuBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.TopCenterActionHost
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MovementsMenuActivity : AppCompatActivity(), TopCenterActionHost {
    companion object {
        @Volatile
        private var cacheNoticeShownInOfflineSession = false
    }

    private lateinit var binding: ActivityMovementsMenuBinding
    private lateinit var snack: SendSnack
    private val gson = Gson()
    private lateinit var cacheStore: CacheStore

    private var items: List<MovementRowUi> = emptyList()
    private var allItems: List<MovementRowUi> = emptyList()
    private lateinit var adapter: MovementsListAdapter
    private var productNamesById: Map<Int, String> = emptyMap()

    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var filteredItems: List<MovementRowUi> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false
    private var pendingSearchNotFoundDialog = false
    private var bulkProductNamesCache: Map<Int, String>? = null
    private var bulkProductNamesCacheAtMs: Long = 0L
    private val bulkProductNamesCacheTtlMs = 30_000L

    private var createDialog: AlertDialog? = null
    private var searchDialog: AlertDialog? = null
    private var locationDropdownValues: List<String> = listOf("")
    private val createTypeValues = listOf("", "IN", "OUT", "ADJUST", "TRANSFER")
    private val searchTypeValues = listOf("", "IN", "OUT", "ADJUST")
    private val sourceValues = listOf("", "SCAN", "MANUAL")
    private var createTypeInput: String = ""
    private var createSourceInput: String = ""
    private var createProductInput: String = ""
    private var createQuantityInput: String = ""
    private var createLocationInput: String = ""
    private var createToLocationInput: String = ""
    private var searchTypeFilter: String = ""
    private var searchSourceFilter: String = ""
    private var searchProductFilter: String = ""
    private var quantityHint: String = "Cantidad"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementsMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        binding.btnRefreshMovements.setImageResource(R.drawable.glass_refresh)
        applyMovementsTitleGradient()
        applyRefreshIconTint()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)
        cacheStore = CacheStore.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = MovementsListAdapter()
        binding.rvMovements.layoutManager = LinearLayoutManager(this)
        binding.rvMovements.adapter = adapter
        lifecycleScope.launch {
            NetworkModule.offlineState.collectLatest { offline ->
                if (!offline) {
                    cacheNoticeShownInOfflineSession = false
                }
            }
        }

        loadLocationOptions()

        binding.btnRefreshMovements.setOnClickListener {
            invalidateBulkProductNamesCache()
            loadMovements(withSnack = true)
        }

        binding.btnPrevPageMovements.setOnClickListener {
            if (hasActiveFilters()) {
                if (filteredOffset <= 0) return@setOnClickListener
                filteredOffset = (filteredOffset - pageSize).coerceAtLeast(0)
                applyFilteredPage()
                binding.rvMovements.scrollToPosition(0)
                return@setOnClickListener
            }
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadMovements(withSnack = false)
            binding.rvMovements.scrollToPosition(0)
        }
        binding.btnNextPageMovements.setOnClickListener {
            if (hasActiveFilters()) {
                val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
                if (shown >= filteredItems.size) return@setOnClickListener
                filteredOffset += pageSize
                applyFilteredPage()
                binding.rvMovements.scrollToPosition(0)
                return@setOnClickListener
            }
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadMovements(withSnack = false)
            binding.rvMovements.scrollToPosition(0)
        }

        applyPagerButtonStyle(binding.btnPrevPageMovements, enabled = false)
        applyPagerButtonStyle(binding.btnNextPageMovements, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadMovements(withSnack = false)
        updateMovementsListAdaptiveHeight()
    }

    override fun onTopCreateAction() {
        openCreateMovementDialog()
    }

    override fun onTopFilterAction() {
        openSearchMovementDialog()
    }

    private fun createMovement(
        typeRawInput: String,
        sourceRawInput: String,
        productInputRaw: String,
        quantityInputRaw: String,
        locationRawInput: String,
        toLocationRawInput: String,
        onFinished: () -> Unit = {}
    ) {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear movimientos.",
                R.drawable.ic_lock
            )
            onFinished()
            return
        }

        val type = typeRawInput.trim().uppercase()
        val sourceRaw = sourceRawInput.trim().uppercase()
        val productInput = productInputRaw.trim()
        val quantity = quantityInputRaw.trim().toIntOrNull()
        val location = normalizeLocationInput(locationRawInput.trim())
        val toLocation = normalizeLocationInput(toLocationRawInput.trim())

        if (type.isBlank()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se pudo crear movimiento",
                details = "Tipo requerido"
            )
            onFinished()
            return
        }

        val source = when (sourceRaw) {
            "SCAN" -> MovementSourceDto.SCAN
            "MANUAL" -> MovementSourceDto.MANUAL
            else -> {
                CreateUiFeedback.showErrorPopup(
                    activity = this,
                    title = "No se pudo crear movimiento",
                    details = "Usa SCAN o MANUAL"
                )
                onFinished()
                return
            }
        }

        if (productInput.isBlank()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se pudo crear movimiento",
                details = "Producto requerido"
            )
            onFinished()
            return
        }
        if (location.isBlank()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se pudo crear movimiento",
                details = "Ubicacion requerida"
            )
            onFinished()
            return
        }

        when (type) {
            "IN", "OUT" -> if (quantity == null || quantity <= 0) {
                CreateUiFeedback.showErrorPopup(
                    activity = this,
                    title = "No se pudo crear movimiento",
                    details = "Cantidad > 0"
                )
                onFinished()
                return
            }
            "ADJUST" -> if (quantity == null || quantity == 0) {
                CreateUiFeedback.showErrorPopup(
                    activity = this,
                    title = "No se pudo crear movimiento",
                    details = "Delta != 0"
                )
                onFinished()
                return
            }
            "TRANSFER" -> {
                if (quantity == null || quantity <= 0) {
                    CreateUiFeedback.showErrorPopup(
                        activity = this,
                        title = "No se pudo crear movimiento",
                        details = "Cantidad > 0"
                    )
                    onFinished()
                    return
                }
                if (toLocation.isBlank()) {
                    CreateUiFeedback.showErrorPopup(
                        activity = this,
                        title = "No se pudo crear movimiento",
                        details = "Ubicacion destino requerida"
                    )
                    onFinished()
                    return
                }
                if (location.equals(toLocation, ignoreCase = true)) {
                    CreateUiFeedback.showErrorPopup(
                        activity = this,
                        title = "No se pudo crear movimiento",
                        details = "Origen y destino no pueden ser iguales"
                    )
                    onFinished()
                    return
                }
            }
            else -> {
                CreateUiFeedback.showErrorPopup(
                    activity = this,
                    title = "No se pudo crear movimiento",
                    details = "Usa IN / OUT / ADJUST / TRANSFER"
                )
                onFinished()
                return
            }
        }

        val loading = CreateUiFeedback.showLoading(this, "movimiento")

        lifecycleScope.launch {
            var loadingHandled = false
            try {
                val productId = productInput.toIntOrNull() ?: resolveProductIdByName(productInput)
                if (productId == null) {
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showErrorPopup(
                            activity = this@MovementsMenuActivity,
                            title = "No se pudo crear movimiento",
                            details = "Producto invalido"
                        )
                    }
                    return@launch
                }

                when (type) {
                    "IN" -> {
                        val dto = MovementOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementIn(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            val productLabel = productNamesById[productId] ?: productId.toString()
                            val details = "ID: ${body.movement.id}\nTipo: IN\nProducto: $productLabel\nCantidad: ${body.movement.quantity}\nUbicacion: ${body.movement.location ?: location}"
                            loadingHandled = true
                            loading.dismissThen {
                                CreateUiFeedback.showCreatedPopup(this@MovementsMenuActivity, "Movimiento creado", details)
                            }
                            createQuantityInput = ""
                            cacheStore.invalidatePrefix("movements")
                            loadMovements(withSnack = false)
                        } else {
                            loadingHandled = true
                            handleMovementError(res.code(), res.errorBody()?.string(), loading)
                        }
                    }
                    "OUT" -> {
                        val dto = MovementOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementOut(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            val productLabel = productNamesById[productId] ?: productId.toString()
                            val details = "ID: ${body.movement.id}\nTipo: OUT\nProducto: $productLabel\nCantidad: ${body.movement.quantity}\nUbicacion: ${body.movement.location ?: location}"
                            loadingHandled = true
                            loading.dismissThen {
                                CreateUiFeedback.showCreatedPopup(this@MovementsMenuActivity, "Movimiento creado", details)
                            }
                            createQuantityInput = ""
                            cacheStore.invalidatePrefix("movements")
                            loadMovements(withSnack = false)
                        } else {
                            loadingHandled = true
                            handleMovementError(res.code(), res.errorBody()?.string(), loading)
                        }
                    }
                    "ADJUST" -> {
                        val dto = MovementAdjustOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementAdjust(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            val productLabel = productNamesById[productId] ?: productId.toString()
                            val details = "ID: ${body.movement.id}\nTipo: ADJUST\nProducto: $productLabel\nDelta: ${body.movement.delta ?: quantity}\nUbicacion: ${body.movement.location ?: location}"
                            loadingHandled = true
                            loading.dismissThen {
                                CreateUiFeedback.showCreatedPopup(this@MovementsMenuActivity, "Movimiento creado", details)
                            }
                            createQuantityInput = ""
                            cacheStore.invalidatePrefix("movements")
                            loadMovements(withSnack = false)
                        } else {
                            loadingHandled = true
                            handleMovementError(res.code(), res.errorBody()?.string(), loading)
                        }
                    }
                    "TRANSFER" -> {
                        val dto = MovementTransferOperationRequest(productId, quantity!!, location, toLocation, source)
                        val res = NetworkModule.api.movementTransfer(dto)
                        if (res.isSuccessful && res.body() != null) {
                            val body = res.body()!!
                            val productLabel = productNamesById[productId] ?: productId.toString()
                            val details = "Transfer ID: ${body.outMovement.transferId ?: "-"}\nProducto: $productLabel\nCantidad: ${body.outMovement.quantity}\nOrigen: ${body.outMovement.location ?: location}\nDestino: ${body.inMovement.location ?: toLocation}"
                            loadingHandled = true
                            loading.dismissThen {
                                CreateUiFeedback.showCreatedPopup(this@MovementsMenuActivity, "Transferencia creada", details)
                            }
                            createQuantityInput = ""
                            cacheStore.invalidatePrefix("movements")
                            loadMovements(withSnack = false)
                        } else {
                            loadingHandled = true
                            handleMovementError(res.code(), res.errorBody()?.string(), loading)
                        }
                    }
                }
            } catch (e: IOException) {
                enqueueOffline(type, productInput, quantity, location, toLocation, source)
                val label = if (productInput.isBlank()) "-" else productInput
                val details = when (type) {
                    "TRANSFER" -> "Producto: $label\nCantidad: ${quantity ?: 0}\nOrigen: $location\nDestino: $toLocation (offline)"
                    "ADJUST" -> "Producto: $label\nDelta: ${quantity ?: 0}\nUbicacion: $location (offline)"
                    else -> "Producto: $label\nCantidad: ${quantity ?: 0}\nUbicacion: $location (offline)"
                }
                loadingHandled = true
                loading.dismissThen {
                    CreateUiFeedback.showCreatedPopup(
                        this@MovementsMenuActivity,
                        "Movimiento creado (offline)",
                        details,
                        accentColorRes = R.color.offline_text
                    )
                }
                loadMovements(withSnack = false)
            } catch (e: Exception) {
                loadingHandled = true
                val details = if (canShowTechnicalMovementErrors()) {
                    "Ha ocurrido un error inesperado.\n${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}"
                } else {
                    "Ha ocurrido un error inesperado al crear el movimiento."
                }
                loading.dismissThen {
                    CreateUiFeedback.showErrorPopup(
                        activity = this@MovementsMenuActivity,
                        title = "No se pudo crear movimiento",
                        details = details
                    )
                }
            } finally {
                if (!loadingHandled) {
                    loading.dismiss()
                }
                onFinished()
            }
        }
    }

    private fun handleMovementError(
        code: Int,
        body: String?,
        loading: CreateUiFeedback.LoadingHandle
    ) {
        if (code == 403) {
            loading.dismissThen {
                UiNotifier.showBlocking(
                    this,
                    "Permisos insuficientes",
                    "No tienes permisos para crear movimientos.",
                    R.drawable.ic_lock
                )
            }
        } else {
            val details = formatMovementCreateError(code, body)
            loading.dismissThen {
                CreateUiFeedback.showErrorPopup(
                    activity = this,
                    title = "No se pudo crear movimiento",
                    details = details
                )
            }
        }
    }

    private fun canShowTechnicalMovementErrors(): Boolean {
        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        return role.equals("ADMIN", ignoreCase = true) || role.equals("MANAGER", ignoreCase = true)
    }

    private fun isUserRole(): Boolean {
        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        return role.equals("USER", ignoreCase = true)
    }

    private fun formatMovementCreateError(code: Int, rawError: String?): String {
        val raw = rawError?.trim().orEmpty()
        val normalized = raw.lowercase()
        val technical = canShowTechnicalMovementErrors()

        val looksProductNotFound =
            (normalized.contains("product") || normalized.contains("producto")) &&
                (normalized.contains("not found") || normalized.contains("no existe") || normalized.contains("inexist"))
        val looksInsufficientStock =
            (normalized.contains("stock") || normalized.contains("inventario")) &&
                (normalized.contains("insufficient") || normalized.contains("insuf") || normalized.contains("not enough"))

        if (looksProductNotFound) {
            return if (technical) {
                buildString {
                    append("Producto no existe.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactMovementErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "No existe el producto indicado. Revisa el ID o el nombre."
            }
        }

        if (looksInsufficientStock) {
            return if (technical) {
                buildString {
                    append("Stock insuficiente para completar la salida o traslado.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactMovementErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "No hay stock suficiente para realizar ese movimiento."
            }
        }

        return if (technical) {
            buildString {
                append(
                    when (code) {
                        400, 422 -> "Datos inválidos para crear movimiento."
                        404 -> "Recurso no encontrado para crear movimiento."
                        409 -> "Conflicto al crear movimiento."
                        500 -> "Error interno del servidor al crear movimiento."
                        else -> "No se pudo crear el movimiento."
                    }
                )
                if (raw.isNotBlank()) append("\nDetalle: ${compactMovementErrorDetail(raw)}")
                if (code > 0) append("\nHTTP $code")
            }
        } else {
            when (code) {
                400, 422 -> "No se pudo crear el movimiento. Revisa los datos."
                404 -> "No se encontró el recurso indicado para el movimiento."
                409 -> "No se pudo crear el movimiento por conflicto de datos."
                500 -> "No se pudo crear el movimiento por un problema del servidor."
                else -> "No se pudo crear el movimiento. Inténtalo de nuevo."
            }
        }
    }

    private fun compactMovementErrorDetail(raw: String, maxLen: Int = 180): String {
        val singleLine = raw.replace("\\s+".toRegex(), " ").trim()
        return if (singleLine.length <= maxLen) singleLine else singleLine.take(maxLen) + "..."
    }

    private fun enqueueOffline(
        type: String,
        productInput: String,
        quantity: Int?,
        location: String,
        toLocation: String,
        source: MovementSourceDto
    ) {
        val productId = productInput.toIntOrNull() ?: -1
        when (type) {
            "IN" -> {
                val dto = MovementOperationRequest(productId, quantity ?: 1, location, source)
                OfflineQueue(this).enqueue(PendingType.MOVEMENT_IN, gson.toJson(dto))
            }
            "OUT" -> {
                val dto = MovementOperationRequest(productId, quantity ?: 1, location, source)
                OfflineQueue(this).enqueue(PendingType.MOVEMENT_OUT, gson.toJson(dto))
            }
            "ADJUST" -> {
                val dto = MovementAdjustOperationRequest(productId, quantity ?: 1, location, source)
                OfflineQueue(this).enqueue(PendingType.MOVEMENT_ADJUST, gson.toJson(dto))
            }
            "TRANSFER" -> {
                val dto = MovementTransferOperationRequest(
                    productId,
                    quantity ?: 1,
                    location,
                    if (toLocation.isBlank()) location else toLocation,
                    source
                )
                OfflineQueue(this).enqueue(PendingType.MOVEMENT_TRANSFER, gson.toJson(dto))
            }
        }
    }

    private fun loadMovements(withSnack: Boolean) {
        if (isLoading) return
        isLoading = true
        var postLoadingNotice: (() -> Unit)? = null
        val loading = if (withSnack) {
            CreateUiFeedback.showListLoading(
                this,
                message = "Cargando movimientos",
                animationRes = R.raw.glass_loading_list,
                minCycles = 2
            )
        } else {
            null
        }

        val filtersActive = hasActiveFilters()
        val effectiveLimit = if (filtersActive) 100 else pageSize
        val effectiveOffset = if (filtersActive) 0 else currentOffset
        if (filtersActive) currentOffset = 0

        val productRaw = searchProductFilter.trim()
        val productIdFilter = productRaw.toIntOrNull()
        val typeFilter = parseTypeForApi(searchTypeFilter)
        val sourceFilter = parseSourceForApi(searchSourceFilter)
        val pendingAll = buildPendingRows()
        val pendingTotalCount = pendingAll.size

        lifecycleScope.launch {
            val cachedRemoteTotal = resolveCachedMovementsRemoteTotal(
                productIdFilter = productIdFilter,
                typeFilter = typeFilter?.name,
                sourceFilter = sourceFilter?.name,
                effectiveLimit = effectiveLimit
            )
            try {
                val cacheKey = CacheKeys.list(
                    "movements",
                    mapOf(
                        "product_id" to productIdFilter,
                        "type" to typeFilter?.name,
                        "source" to sourceFilter?.name,
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cached = cacheStore.get(cacheKey, MovementListResponseDto::class.java)
                if (cached != null) {
                    val pending = pendingMovementsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cached.total,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    val ids = (pending.map { it.productId } + cached.items.map { it.productId }).toSet()
                    productNamesById = resolveProductNames(ids)
                    val mappedRemote = cached.items.map { it.toRowUi(productNamesById) }
                    val ordered = (pending + mappedRemote).sortedByDescending { it.id }
                    totalCount = cached.total + pendingTotalCount
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pending.size)
                    isLoading = false
                }
                val res = NetworkModule.api.listMovements(
                    productId = productIdFilter,
                    movementType = typeFilter,
                    movementSource = sourceFilter,
                    userId = null,
                    dateFrom = null,
                    dateTo = null,
                    limit = effectiveLimit,
                    offset = effectiveOffset
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    cacheStore.put(cacheKey, body)
                    val pending = pendingMovementsForPage(
                        offset = effectiveOffset,
                        remoteTotal = body.total,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    val remoteItems = body.items
                    totalCount = body.total + pendingTotalCount

                    val ids = (pending.map { it.productId } + remoteItems.map { it.productId }).toSet()
                    productNamesById = resolveProductNames(ids)

                    val mappedRemote = remoteItems.map { it.toRowUi(productNamesById) }
                    val ordered = (pending + mappedRemote).sortedByDescending { it.id }
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pending.size)
                    if (withSnack) {
                        postLoadingNotice = {
                            CreateUiFeedback.showStatusPopup(
                                activity = this@MovementsMenuActivity,
                                title = "Movimientos cargados",
                                details = "Se han cargado correctamente.",
                                animationRes = R.raw.correct_create,
                                autoDismissMs = 2500L
                            )
                        }
                    }
                } else {
                    if (withSnack) snack.showError("Error ${res.code()}: ${res.errorBody()?.string()}")
                    val cachedOnError = cacheStore.get(cacheKey, MovementListResponseDto::class.java)
                    if (cachedOnError != null) {
                        val pending = pendingMovementsForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedOnError.total,
                            filtersActive = filtersActive,
                            pendingAll = pendingAll
                        )
                        val ids = (pending.map { it.productId } + cachedOnError.items.map { it.productId }).toSet()
                        productNamesById = resolveProductNames(ids)
                        val mappedRemote = cachedOnError.items.map { it.toRowUi(productNamesById) }
                        val ordered = (pending + mappedRemote).sortedByDescending { it.id }
                        totalCount = cachedOnError.total + pendingTotalCount
                        setAllItemsAndApplyFilters(ordered)
                        updatePageInfo(ordered.size, pending.size)
                        if (withSnack && !cacheNoticeShownInOfflineSession) {
                            postLoadingNotice = { showMovementsCacheNoticeOnce() }
                        } else {
                            showMovementsCacheNoticeOnce()
                        }
                    } else {
                        val pending = pendingMovementsForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedRemoteTotal,
                            filtersActive = filtersActive,
                            pendingAll = pendingAll
                        )
                        productNamesById = resolveProductNames(pending.map { it.productId }.toSet())
                        val ordered = pending.sortedByDescending { it.id }
                        totalCount = cachedRemoteTotal + pendingTotalCount
                        setAllItemsAndApplyFilters(ordered)
                        updatePageInfo(ordered.size, ordered.size)
                    }
                }
            } catch (e: Exception) {
                if (withSnack && e !is IOException) {
                    snack.showError("Error de red: ${e.message}")
                }
                val cacheKey = CacheKeys.list(
                    "movements",
                    mapOf(
                        "product_id" to productIdFilter,
                        "type" to typeFilter?.name,
                        "source" to sourceFilter?.name,
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cachedOnError = cacheStore.get(cacheKey, MovementListResponseDto::class.java)
                if (cachedOnError != null) {
                    val pending = pendingMovementsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedOnError.total,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    val ids = (pending.map { it.productId } + cachedOnError.items.map { it.productId }).toSet()
                    productNamesById = resolveProductNames(ids)
                    val mappedRemote = cachedOnError.items.map { it.toRowUi(productNamesById) }
                    val ordered = (pending + mappedRemote).sortedByDescending { it.id }
                    totalCount = cachedOnError.total + pendingTotalCount
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pending.size)
                    if (withSnack && !cacheNoticeShownInOfflineSession) {
                        postLoadingNotice = { showMovementsCacheNoticeOnce() }
                    } else {
                        showMovementsCacheNoticeOnce()
                    }
                } else {
                    val pending = pendingMovementsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedRemoteTotal,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    productNamesById = resolveProductNames(pending.map { it.productId }.toSet())
                    val ordered = pending.sortedByDescending { it.id }
                    totalCount = cachedRemoteTotal + pendingTotalCount
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, ordered.size)
                }
            } finally {
                if (loading != null) {
                    val action = postLoadingNotice
                    if (action != null) {
                        loading.dismissThen { action() }
                    } else {
                        loading.dismiss()
                    }
                } else {
                    postLoadingNotice?.invoke()
                }
                isLoading = false
                if (pendingFilterApply) {
                    pendingFilterApply = false
                    val showDialog = pendingSearchNotFoundDialog
                    pendingSearchNotFoundDialog = false
                    applySearchFiltersInternal(allowReload = false, showNotFoundDialog = showDialog)
                }
            }
        }
    }

    private fun buildPendingRows(): List<MovementRowUi> {
        val queue = OfflineQueue(this)
        val pending = queue.getAll()
        val out = mutableListOf<MovementRowUi>()

        pending.forEachIndexed { index, p ->
            when (p.type) {
                PendingType.MOVEMENT_IN -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        out.add(
                            MovementRowUi(
                                id = -1 - index,
                                movementType = "IN",
                                productId = dto.productId,
                                productName = null,
                                quantity = dto.quantity,
                                delta = null,
                                location = dto.location,
                                source = dto.movementSource.name,
                                createdAt = "offline",
                                transferId = null,
                                isPending = true
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_OUT -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        out.add(
                            MovementRowUi(
                                id = -1 - index,
                                movementType = "OUT",
                                productId = dto.productId,
                                productName = null,
                                quantity = dto.quantity,
                                delta = null,
                                location = dto.location,
                                source = dto.movementSource.name,
                                createdAt = "offline",
                                transferId = null,
                                isPending = true
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_ADJUST -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementAdjustOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        out.add(
                            MovementRowUi(
                                id = -1 - index,
                                movementType = "ADJUST",
                                productId = dto.productId,
                                productName = null,
                                quantity = dto.delta,
                                delta = dto.delta,
                                location = dto.location,
                                source = dto.movementSource.name,
                                createdAt = "offline",
                                transferId = null,
                                isPending = true
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_TRANSFER -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementTransferOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        out.add(
                            MovementRowUi(
                                id = -1 - index,
                                movementType = "TRANSFER",
                                productId = dto.productId,
                                productName = null,
                                quantity = dto.quantity,
                                delta = null,
                                location = "${dto.fromLocation} -> ${dto.toLocation}",
                                source = dto.movementSource.name,
                                createdAt = "offline",
                                transferId = "offline",
                                isPending = true
                            )
                        )
                    }
                }
                else -> Unit
            }
        }

        return out
    }

    private fun pendingMovementsForPage(
        offset: Int,
        remoteTotal: Int,
        filtersActive: Boolean,
        pendingAll: List<MovementRowUi>
    ): List<MovementRowUi> {
        if (pendingAll.isEmpty()) return emptyList()
        if (filtersActive) return pendingAll
        val startInPending = (offset - remoteTotal).coerceAtLeast(0)
        if (startInPending >= pendingAll.size) return emptyList()
        val endInPending = (offset + pageSize - remoteTotal)
            .coerceAtMost(pendingAll.size)
            .coerceAtLeast(startInPending)
        return pendingAll.subList(startInPending, endInPending)
    }

    private suspend fun resolveCachedMovementsRemoteTotal(
        productIdFilter: Int?,
        typeFilter: String?,
        sourceFilter: String?,
        effectiveLimit: Int
    ): Int {
        val keyAtStart = CacheKeys.list(
            "movements",
            mapOf(
                "product_id" to productIdFilter,
                "type" to typeFilter,
                "source" to sourceFilter,
                "limit" to effectiveLimit,
                "offset" to 0
            )
        )
        val cachedAtStart = cacheStore.get(keyAtStart, MovementListResponseDto::class.java)
        if (cachedAtStart != null) return cachedAtStart.total
        if (effectiveLimit != pageSize) {
            val keyDefault = CacheKeys.list(
                "movements",
                mapOf(
                    "product_id" to productIdFilter,
                    "type" to typeFilter,
                    "source" to sourceFilter,
                    "limit" to pageSize,
                    "offset" to 0
                )
            )
            val cachedDefault = cacheStore.get(keyDefault, MovementListResponseDto::class.java)
            if (cachedDefault != null) return cachedDefault.total
        }
        return 0
    }

    private suspend fun resolveProductNames(ids: Set<Int>): Map<Int, String> {
        if (ids.isEmpty()) return emptyMap()
        val resolved = mutableMapOf<Int, String>()
        val cachedMap = getCachedProductNames()
        val bulkNames = getOrFetchBulkProductNames()
        ids.forEach { id ->
            bulkNames[id]?.let { resolved[id] = it }
        }
        for (id in ids) {
            if (resolved.containsKey(id)) continue
            val singleRes = runCatching { NetworkModule.api.getProduct(id) }.getOrNull()
            if (singleRes?.isSuccessful == true && singleRes.body() != null) {
                val name = singleRes.body()!!.name
                resolved[id] = name
                updateProductNameCache(listOf(singleRes.body()!!))
            } else {
                cachedMap[id]?.let { resolved[id] = it }
            }
        }
        ids.forEach { id ->
            if (!resolved.containsKey(id)) cachedMap[id]?.let { resolved[id] = it }
        }
        return resolved
    }

    private suspend fun getOrFetchBulkProductNames(): Map<Int, String> {
        val now = System.currentTimeMillis()
        val cached = bulkProductNamesCache
        if (cached != null && (now - bulkProductNamesCacheAtMs) < bulkProductNamesCacheTtlMs) {
            return cached
        }
        val listRes = runCatching {
            NetworkModule.api.listProducts(
                orderBy = "id",
                orderDir = "asc",
                limit = 100,
                offset = 0
            )
        }.getOrNull()
        if (listRes?.isSuccessful == true && listRes.body() != null) {
            val items = listRes.body()!!.items
            val map = items.associate { it.id to it.name }
            bulkProductNamesCache = map
            bulkProductNamesCacheAtMs = now
            updateProductNameCache(items)
            return map
        }
        return emptyMap()
    }

    private fun invalidateBulkProductNamesCache() {
        bulkProductNamesCache = null
        bulkProductNamesCacheAtMs = 0L
    }

    private suspend fun getCachedProductNames(): Map<Int, String> {
        val cached = cacheStore.get("products:names", ProductNameCache::class.java)
        return cached?.items?.associateBy({ it.id }, { it.name }) ?: emptyMap()
    }

    private suspend fun updateProductNameCache(items: List<com.example.inventoryapp.data.remote.model.ProductResponseDto>) {
        if (items.isEmpty()) return
        val existing = cacheStore.get("products:names", ProductNameCache::class.java)
        val map = existing?.items?.associateBy({ it.id }, { it.name })?.toMutableMap() ?: mutableMapOf()
        items.forEach { p -> map[p.id] = p.name }
        val merged = map.entries.map { ProductNameItem(it.key, it.value) }
        cacheStore.put("products:names", ProductNameCache(merged))
    }

    private suspend fun resolveProductIdByName(input: String): Int? {
        val needle = input.trim().lowercase()
        if (needle.isBlank()) return null
        val cached = productNamesById.entries.firstOrNull { it.value.lowercase() == needle }
        if (cached != null) return cached.key

        val res = runCatching { NetworkModule.api.listProducts(name = input, limit = 50, offset = 0) }.getOrNull()
        if (res?.isSuccessful == true && res.body() != null) {
            val match = res.body()!!.items.firstOrNull { it.name.equals(input, ignoreCase = true) }
            if (match != null) return match.id
            return res.body()!!.items.firstOrNull()?.id
        }
        return null
    }

    private fun MovementResponseDto.toRowUi(productNames: Map<Int, String>): MovementRowUi {
        return MovementRowUi(
            id = id,
            movementType = movementType.name,
            productId = productId,
            productName = productNames[productId],
            quantity = quantity,
            delta = delta,
            location = location ?: locationId?.toString() ?: "n/a",
            source = movementSource.name,
            createdAt = createdAt,
            transferId = transferId,
            isPending = false
        )
    }

    private fun setAllItemsAndApplyFilters(ordered: List<MovementRowUi>) {
        allItems = ordered
        if (hasActiveFilters() || pendingFilterApply) {
            applySearchFiltersInternal(allowReload = false)
        } else {
            items = ordered
            adapter.submit(toAdapterRows(ordered))
            updateMovementsListAdaptiveHeight()
        }
    }

    private fun toAdapterRows(rows: List<MovementRowUi>): List<MovementRow> {
        return rows.map { row ->
            val typeLabel = if (row.transferId != null || row.movementType == "TRANSFER") "TRANSFER" else row.movementType
            val productLabel = row.productName ?: "Producto ${row.productId}"
            val qtyValue = row.delta ?: row.quantity
            val qtyLabel = formatQuantity(qtyValue)
            val title = "Producto: $productLabel"
            val meta = "Cantidad: $qtyLabel  ·  Localización: ${row.location}"
            val sub = "Src ${row.source}  ·  ${row.createdAt}"
            MovementRow(
                movementId = row.id,
                type = typeLabel,
                title = title,
                meta = meta,
                sub = sub,
                isPending = row.isPending
            )
        }
    }

    private fun formatQuantity(value: Int): String {
        val nf = NumberFormat.getIntegerInstance(Locale("es", "ES"))
        return nf.format(value)
    }

    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        if (hasActiveFilters()) {
            val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
            val currentPage = if (filteredItems.isEmpty()) 0 else (filteredOffset / pageSize) + 1
            val totalPages = if (filteredItems.isEmpty()) 0 else ((filteredItems.size + pageSize - 1) / pageSize)
            binding.tvMovementsPageNumber.text = "Pagina $currentPage/$totalPages"
            binding.tvMovementsPageInfo.text = "Mostrando $shown/${filteredItems.size}"
            val prevEnabled = filteredOffset > 0
            val nextEnabled = shown < filteredItems.size
            binding.btnPrevPageMovements.isEnabled = prevEnabled
            binding.btnNextPageMovements.isEnabled = nextEnabled
            applyPagerButtonStyle(binding.btnPrevPageMovements, prevEnabled)
            applyPagerButtonStyle(binding.btnNextPageMovements, nextEnabled)
            return
        }
        val shownOnline = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        val totalItems = if (totalCount > 0) totalCount else pendingCount
        val currentPage = if (totalItems <= 0) 0 else (currentOffset / pageSize) + 1
        val totalPages = if (totalItems <= 0) 0 else ((totalItems + pageSize - 1) / pageSize)
        val label = if (totalItems > 0) {
            "Mostrando $shownOnline/$totalItems"
        } else {
            "Mostrando 0/0"
        }
        binding.tvMovementsPageNumber.text = "Pagina $currentPage/$totalPages"
        binding.tvMovementsPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shownOnline < totalItems
        binding.btnPrevPageMovements.isEnabled = prevEnabled
        binding.btnNextPageMovements.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevPageMovements, prevEnabled)
        applyPagerButtonStyle(binding.btnNextPageMovements, nextEnabled)
    }

    private fun applyPagerButtonStyle(button: Button, enabled: Boolean) {
        button.backgroundTintList = null
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (!enabled) {
            val colors = intArrayOf(
                if (isDark) 0x334F6480 else 0x33A7BED8,
                if (isDark) 0x33445A74 else 0x338FA9C6
            )
            val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
                cornerRadius = resources.displayMetrics.density * 16f
                setStroke((resources.displayMetrics.density * 1f).toInt(), if (isDark) 0x44AFCBEB else 0x5597BCD9)
            }
            button.background = drawable
            button.setTextColor(ContextCompat.getColor(this, R.color.liquid_popup_hint))
            return
        }
        val colors = intArrayOf(
            if (isDark) 0x66789BC4 else 0x99D6EBFA.toInt(),
            if (isDark) 0x666D8DB4 else 0x99C5E0F4.toInt()
        )
        val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = resources.displayMetrics.density * 16f
            setStroke((resources.displayMetrics.density * 1f).toInt(), if (isDark) 0x88B5D5F4.toInt() else 0x88A7CBE6.toInt())
        }
        button.background = drawable
        button.setTextColor(ContextCompat.getColor(this, R.color.liquid_popup_button_text))
    }

    private fun applyRefreshIconTint() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (!isDark) {
            val blue = ContextCompat.getColor(this, R.color.icon_grad_mid2)
            binding.btnRefreshMovements.setColorFilter(blue)
        } else {
            binding.btnRefreshMovements.clearColorFilter()
        }
    }

    private fun loadLocationOptions() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    cacheStore.put(
                        CacheKeys.list("locations", mapOf("limit" to 200, "offset" to 0)),
                        res.body()!!
                    )
                    val values = items.sortedBy { it.id }
                        .map { "(${it.id}) ${it.code}" }
                        .distinct()
                    locationDropdownValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    return@launch
                }
            } catch (_: Exception) { }

            val cached = cacheStore.get(
                CacheKeys.list("locations", mapOf("limit" to 200, "offset" to 0)),
                com.example.inventoryapp.data.remote.model.LocationListResponseDto::class.java
            )
            if (cached != null) {
                val values = cached.items.sortedBy { it.id }
                    .map { "(${it.id}) ${it.code}" }
                    .distinct()
                locationDropdownValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
            }
        }
    }

    private fun bindDropdown(auto: MaterialAutoCompleteTextView, values: List<String>) {
        val adapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, values)
        auto.setAdapter(adapter)
        auto.setOnClickListener { auto.showDropDown() }
        auto.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) auto.showDropDown() }
    }

    private fun openCreateMovementDialog() {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear movimientos.",
                R.drawable.ic_lock
            )
            return
        }
        if (createDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_movements_create_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCreateMovementDialogClose)
        val btnCreate = view.findViewById<Button>(R.id.btnDialogCreateMovement)
        val etType = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogCreateType)
        val etSource = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogCreateSource)
        val etProduct = view.findViewById<TextInputEditText>(R.id.etDialogCreateProduct)
        val etQuantity = view.findViewById<TextInputEditText>(R.id.etDialogCreateQuantity)
        val etLocation = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogCreateLocation)
        val etToLocation = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogCreateToLocation)
        val tilType = view.findViewById<TextInputLayout>(R.id.tilDialogCreateType)
        val tilSource = view.findViewById<TextInputLayout>(R.id.tilDialogCreateSource)
        val tilLocation = view.findViewById<TextInputLayout>(R.id.tilDialogCreateLocation)
        val tilToLocation = view.findViewById<TextInputLayout>(R.id.tilDialogCreateToLocation)

        etType.setText(createTypeInput, false)
        etSource.setText(createSourceInput, false)
        etProduct.setText(createProductInput)
        etQuantity.setText(createQuantityInput)
        etLocation.setText(createLocationInput, false)
        etToLocation.setText(createToLocationInput, false)

        bindDropdown(etType, createTypeValues)
        bindDropdown(etSource, sourceValues)
        bindDropdown(etLocation, locationDropdownValues)
        bindDropdown(etToLocation, locationDropdownValues)

        val currentType = createTypeInput.trim().uppercase()
        updateDialogTransferVisibility(currentType, tilToLocation)
        updateDialogQuantityForType(currentType, etQuantity)

        etType.addTextChangedListener { text ->
            val type = text?.toString()?.trim()?.uppercase().orEmpty()
            updateDialogTransferVisibility(type, tilToLocation)
            updateDialogQuantityForType(type, etQuantity)
        }

        applyDialogDropdownStyle(
            listOf(tilType, tilSource, tilLocation, tilToLocation),
            listOf(etType, etSource, etLocation, etToLocation)
        )

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            createTypeInput = etType.text?.toString().orEmpty().trim()
            createSourceInput = etSource.text?.toString().orEmpty().trim()
            createProductInput = etProduct.text?.toString().orEmpty().trim()
            createQuantityInput = etQuantity.text?.toString().orEmpty().trim()
            createLocationInput = etLocation.text?.toString().orEmpty().trim()
            createToLocationInput = etToLocation.text?.toString().orEmpty().trim()

            btnCreate.isEnabled = false
            createMovement(
                typeRawInput = createTypeInput,
                sourceRawInput = createSourceInput,
                productInputRaw = createProductInput,
                quantityInputRaw = createQuantityInput,
                locationRawInput = createLocationInput,
                toLocationRawInput = createToLocationInput,
                onFinished = { btnCreate.isEnabled = true }
            )
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            createDialog = null
            hideKeyboard()
        }
        createDialog = dialog
        dialog.show()
    }

    private fun openSearchMovementDialog() {
        if (searchDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_movements_search_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnSearchMovementDialogClose)
        val btnSearch = view.findViewById<Button>(R.id.btnDialogSearchMovement)
        val btnClear = view.findViewById<Button>(R.id.btnDialogClearSearchMovement)
        val etType = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogSearchType)
        val etSource = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogSearchSource)
        val etProduct = view.findViewById<TextInputEditText>(R.id.etDialogSearchProduct)
        val tilType = view.findViewById<TextInputLayout>(R.id.tilDialogSearchType)
        val tilSource = view.findViewById<TextInputLayout>(R.id.tilDialogSearchSource)

        etType.setText(searchTypeFilter, false)
        etSource.setText(searchSourceFilter, false)
        etProduct.setText(searchProductFilter)

        bindDropdown(etType, searchTypeValues)
        bindDropdown(etSource, sourceValues)
        applyDialogDropdownStyle(listOf(tilType, tilSource), listOf(etType, etSource))

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnSearch.setOnClickListener {
            searchTypeFilter = etType.text?.toString().orEmpty().trim()
            searchSourceFilter = etSource.text?.toString().orEmpty().trim()
            searchProductFilter = etProduct.text?.toString().orEmpty().trim()
            hideKeyboard()
            dialog.dismiss()
            applySearchFilters()
        }
        btnClear.setOnClickListener {
            etType.setText("", false)
            etSource.setText("", false)
            etProduct.setText("")
            clearSearchFilters()
            hideKeyboard()
        }

        dialog.setOnDismissListener {
            searchDialog = null
            hideKeyboard()
        }
        searchDialog = dialog
        dialog.show()
    }

    private fun applyDialogDropdownStyle(
        textInputLayouts: List<TextInputLayout>,
        dropdowns: List<MaterialAutoCompleteTextView>
    ) {
        val popupDrawable = ContextCompat.getDrawable(this, R.drawable.bg_liquid_dropdown_popup)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        textInputLayouts.forEach { til ->
            til.setEndIconTintList(null)
            til.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
                GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
            }
        }
        dropdowns.forEach { auto ->
            if (popupDrawable != null) auto.setDropDownBackgroundDrawable(popupDrawable)
        }
    }

    private fun updateDialogTransferVisibility(type: String, tilToLocation: TextInputLayout) {
        tilToLocation.visibility = if (type == "TRANSFER") android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateDialogQuantityForType(type: String, etQuantity: TextInputEditText) {
        if (type == "ADJUST") {
            quantityHint = "(-)Negativo para retirar | (+)Positivo para introducir"
            etQuantity.hint = quantityHint
            etQuantity.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        } else {
            quantityHint = "Cantidad"
            etQuantity.hint = quantityHint
            etQuantity.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    private fun updateMovementsListAdaptiveHeight() {
        binding.scrollMovements.post {
            val topSpacerLp = binding.viewMovementsTopSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val bottomSpacerLp = binding.viewMovementsBottomSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val cardLp = binding.cardMovementsList.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val rvLp = binding.rvMovements.layoutParams as? LinearLayout.LayoutParams ?: return@post

            val visibleCount = if (::adapter.isInitialized) adapter.itemCount else 0
            if (visibleCount in 1 until pageSize) {
                topSpacerLp.height = 0
                topSpacerLp.weight = 1f
                bottomSpacerLp.height = 0
                bottomSpacerLp.weight = 1f
                cardLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                cardLp.weight = 0f
                rvLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                rvLp.weight = 0f
                binding.rvMovements.isNestedScrollingEnabled = false
            } else {
                topSpacerLp.height = 0
                topSpacerLp.weight = 0f
                bottomSpacerLp.height = 0
                bottomSpacerLp.weight = 0f
                cardLp.height = 0
                cardLp.weight = 1f
                rvLp.height = 0
                rvLp.weight = 1f
                binding.rvMovements.isNestedScrollingEnabled = true
            }
            binding.viewMovementsTopSpacer.layoutParams = topSpacerLp
            binding.viewMovementsBottomSpacer.layoutParams = bottomSpacerLp
            binding.cardMovementsList.layoutParams = cardLp
            binding.rvMovements.layoutParams = rvLp
        }
    }

    private fun applyMovementsTitleGradient() {
        binding.tvMovementsTitle.post {
            val paint = binding.tvMovementsTitle.paint
            val width = paint.measureText(binding.tvMovementsTitle.text.toString())
            if (width <= 0f) return@post
            val c1 = ContextCompat.getColor(this, R.color.icon_grad_start)
            val c2 = ContextCompat.getColor(this, R.color.icon_grad_mid2)
            val c3 = ContextCompat.getColor(this, R.color.icon_grad_mid1)
            val c4 = ContextCompat.getColor(this, R.color.icon_grad_end)
            val shader = android.graphics.LinearGradient(
                0f,
                0f,
                width,
                0f,
                intArrayOf(c1, c2, c3, c4),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            binding.tvMovementsTitle.invalidate()
        }
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true, showNotFoundDialog = true)
    }

    private fun applySearchFiltersInternal(allowReload: Boolean, showNotFoundDialog: Boolean = false) {
        val typeRaw = searchTypeFilter.trim().uppercase()
        val sourceRaw = searchSourceFilter.trim().uppercase()
        val productRaw = searchProductFilter.trim()

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > allItems.size)) {
            pendingFilterApply = true
            pendingSearchNotFoundDialog = showNotFoundDialog
            currentOffset = 0
            loadMovements(withSnack = false)
            return
        }

        var filtered = allItems
        if (typeRaw.isNotBlank()) {
            filtered = filtered.filter { row ->
                val typeLabel = if (row.transferId != null || row.movementType == "TRANSFER") "TRANSFER" else row.movementType
                typeLabel.equals(typeRaw, ignoreCase = true)
            }
        }
        if (sourceRaw.isNotBlank()) {
            filtered = filtered.filter { it.source.equals(sourceRaw, ignoreCase = true) }
        }
        if (productRaw.isNotBlank()) {
            val productId = productRaw.toIntOrNull()
            filtered = if (productId != null) {
                filtered.filter { it.productId == productId }
            } else {
                val needle = productRaw.lowercase()
                filtered.filter { (it.productName ?: "").lowercase().contains(needle) }
            }
        }

        filteredItems = filtered
        filteredOffset = 0
        applyFilteredPage()
        if (showNotFoundDialog && hasActiveFilters() && filtered.isEmpty()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se encontraron movimientos",
                details = buildMovementSearchNotFoundDetails(typeRaw, sourceRaw, productRaw),
                animationRes = R.raw.notfound
            )
        }
    }

    private fun clearSearchFilters() {
        searchTypeFilter = ""
        searchSourceFilter = ""
        searchProductFilter = ""
        filteredItems = emptyList()
        filteredOffset = 0
        items = allItems
        adapter.submit(toAdapterRows(allItems))
        updatePageInfo(items.size, items.size)
        updateMovementsListAdaptiveHeight()
    }

    private fun hasActiveFilters(): Boolean {
        return searchTypeFilter.isNotBlank() ||
            searchSourceFilter.isNotBlank() ||
            searchProductFilter.isNotBlank()
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        items = page
        adapter.submit(toAdapterRows(page))
        updatePageInfo(page.size, page.size)
        updateMovementsListAdaptiveHeight()
    }

    private fun parseTypeForApi(raw: String): MovementTypeDto? {
        return when (raw.trim().uppercase()) {
            "IN" -> MovementTypeDto.IN
            "OUT" -> MovementTypeDto.OUT
            "ADJUST" -> MovementTypeDto.ADJUST
            else -> null
        }
    }

    private fun parseSourceForApi(raw: String): MovementSourceDto? {
        return when (raw.trim().uppercase()) {
            "SCAN" -> MovementSourceDto.SCAN
            "MANUAL" -> MovementSourceDto.MANUAL
            else -> null
        }
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun showMovementsCacheNoticeOnce() {
        if (cacheNoticeShownInOfflineSession) return
        UiNotifier.showBlockingTimed(
            this,
            "Mostrando movimientos en cache y pendientes offline",
            R.drawable.sync,
            timeoutMs = 3_200L
        )
        cacheNoticeShownInOfflineSession = true
    }

    private fun buildMovementSearchNotFoundDetails(
        typeRaw: String,
        sourceRaw: String,
        productRaw: String
    ): String {
        val parts = mutableListOf<String>()
        if (typeRaw.isNotBlank()) {
            val typeLabel = when (typeRaw) {
                "IN" -> "de entrada (IN)"
                "OUT" -> "de salida (OUT)"
                "ADJUST" -> "de ajuste (ADJUST)"
                "TRANSFER" -> "de traslado (TRANSFER)"
                else -> "de tipo $typeRaw"
            }
            parts.add(typeLabel)
        }
        if (sourceRaw.isNotBlank()) {
            val sourceLabel = when (sourceRaw) {
                "SCAN" -> "de la fuente SCAN"
                "MANUAL" -> "de la fuente MANUAL"
                else -> "de la fuente $sourceRaw"
            }
            parts.add(sourceLabel)
        }
        if (productRaw.isNotBlank()) {
            val productLabel = if (productRaw.toIntOrNull() != null) {
                "del producto ID $productRaw"
            } else {
                "del producto \"$productRaw\""
            }
            parts.add(productLabel)
        }
        return if (parts.isEmpty()) {
            "No se encontró ningún movimiento con los filtros actuales."
        } else {
            "No se encontró ningún movimiento ${parts.joinToString(separator = " ")}."
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private data class MovementRowUi(
        val id: Int,
        val movementType: String,
        val productId: Int,
        val productName: String?,
        val quantity: Int,
        val delta: Int?,
        val location: String,
        val source: String,
        val createdAt: String,
        val transferId: String?,
        val isPending: Boolean
    )
}
