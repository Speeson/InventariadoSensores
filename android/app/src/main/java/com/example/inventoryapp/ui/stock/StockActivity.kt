package com.example.inventoryapp.ui.stock
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.cache.CacheKeys
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.local.cache.ProductNameCache
import com.example.inventoryapp.data.local.cache.ProductNameItem
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.StockCreateDto
import com.example.inventoryapp.data.remote.model.StockListResponseDto
import com.example.inventoryapp.data.remote.model.StockResponseDto
import com.example.inventoryapp.data.remote.model.StockUpdateDto
import com.example.inventoryapp.databinding.ActivityStockBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.TopCenterActionHost
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import com.example.inventoryapp.ui.common.GradientIconUtil
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject

class StockActivity : AppCompatActivity(), TopCenterActionHost {
    companion object {
        private const val OFFLINE_DELETE_MARKER = "offline_delete"
        @Volatile
        private var cacheNoticeShownInOfflineSession = false
    }

    private lateinit var binding: ActivityStockBinding
    private lateinit var snack: SendSnack
    private lateinit var cacheStore: CacheStore

    private val gson = Gson()
    private var items: List<StockResponseDto> = emptyList()
    private var allItems: List<StockResponseDto> = emptyList()
    private lateinit var adapter: StockListAdapter
    private var productNameById: Map<Int, String> = emptyMap()
    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var filteredItems: List<StockResponseDto> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false
    private var pendingSearchNotFoundDialog = false
    private var bulkProductNamesCache: Map<Int, String>? = null
    private var bulkProductNamesCacheAtMs: Long = 0L
    private val bulkProductNamesCacheTtlMs = 30_000L
    private var createDialog: AlertDialog? = null
    private var searchDialog: AlertDialog? = null
    private var locationDropdownValues: List<String> = listOf("")
    private var createProductInput: String = ""
    private var createLocationInput: String = ""
    private var createQuantityInput: String = ""
    private var searchProductFilter: String = ""
    private var searchLocationFilter: String = ""
    private var searchQuantityFilter: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        binding.btnRefresh.setImageResource(R.drawable.glass_refresh)
        applyStockTitleGradient()
        applyRefreshIconTint()
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)
        cacheStore = CacheStore.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnRefresh.setOnClickListener {
            invalidateBulkProductNamesCache()
            loadStocks(withSnack = true)
        }
        loadLocationOptions()

        adapter = StockListAdapter { stock -> showEditDialog(stock) }
        binding.rvStocks.layoutManager = LinearLayoutManager(this)
        binding.rvStocks.adapter = adapter

        lifecycleScope.launch {
            NetworkModule.offlineState.collectLatest { offline ->
                if (!offline) {
                    cacheNoticeShownInOfflineSession = false
                }
            }
        }

        binding.btnPrevPage.setOnClickListener {
            if (hasActiveFilters()) {
                if (filteredOffset <= 0) return@setOnClickListener
                filteredOffset = (filteredOffset - pageSize).coerceAtLeast(0)
                applyFilteredPage()
                binding.rvStocks.scrollToPosition(0)
                return@setOnClickListener
            }
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadStocks()
            binding.rvStocks.scrollToPosition(0)
        }
        binding.btnNextPage.setOnClickListener {
            if (hasActiveFilters()) {
                val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
                if (shown >= filteredItems.size) return@setOnClickListener
                filteredOffset += pageSize
                applyFilteredPage()
                binding.rvStocks.scrollToPosition(0)
                return@setOnClickListener
            }
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadStocks()
            binding.rvStocks.scrollToPosition(0)
        }

        // Initial button styling
        applyPagerButtonStyle(binding.btnPrevPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextPage, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadStocks(withSnack = false)
        updateStocksListAdaptiveHeight()
    }

    override fun onTopCreateAction() {
        openCreateStockDialog()
    }

    override fun onTopFilterAction() {
        openSearchStockDialog()
    }

    private fun loadStocks(withSnack: Boolean = false) {
        if (isLoading) return
        isLoading = true
        var postLoadingNotice: (() -> Unit)? = null
        val loading = if (withSnack) {
            CreateUiFeedback.showListLoading(
                this,
                message = "Cargando stock",
                animationRes = R.raw.glass_loading_list,
                minCycles = 2
            )
        } else {
            null
        }
        lifecycleScope.launch {
            try {
                val filtersActive = hasActiveFilters()
                val effectiveLimit = if (filtersActive) 100 else pageSize
                val effectiveOffset = if (filtersActive) 0 else currentOffset
                if (filtersActive) currentOffset = 0
                val pendingTotalCount = pendingStocksCount()
                val cachedRemoteTotal = resolveCachedRemoteTotal(effectiveLimit)
                val cacheKey = CacheKeys.list(
                    "stocks",
                    mapOf(
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cached = cacheStore.get(cacheKey, StockListResponseDto::class.java)
                if (cached != null) {
                    val pending = pendingStocksForPage(
                        offset = effectiveOffset,
                        remoteTotal = cached.total,
                        filtersActive = filtersActive
                    )
                    val ordered = markPendingDeletedStocks(cached.items + pending)
                    productNameById = resolveProductNames(ordered)
                    totalCount = cached.total + pendingTotalCount
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pending.size)
                }
                val res = NetworkModule.api.listStocks(limit = effectiveLimit, offset = effectiveOffset)
                if (res.isSuccessful && res.body() != null) {
                    cacheStore.put(cacheKey, res.body()!!)
                    val pending = pendingStocksForPage(
                        offset = effectiveOffset,
                        remoteTotal = res.body()!!.total,
                        filtersActive = filtersActive
                    )
                    val pageItems = res.body()!!.items
                    totalCount = res.body()!!.total + pendingTotalCount
                    val ordered = markPendingDeletedStocks(pageItems + pending)
                    productNameById = resolveProductNames(ordered)
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pending.size)
                    if (withSnack) {
                        postLoadingNotice = {
                            CreateUiFeedback.showStatusPopup(
                                activity = this@StockActivity,
                                title = "Stock cargado",
                                details = "Se ha cargado correctamente.",
                                animationRes = R.raw.correct_create,
                                autoDismissMs = 2500L
                            )
                        }
                    }
                } else {
                    val cachedOnError = cacheStore.get(cacheKey, StockListResponseDto::class.java)
                    if (cachedOnError != null) {
                        val pending = pendingStocksForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedOnError.total,
                            filtersActive = filtersActive
                        )
                        val ordered = markPendingDeletedStocks(cachedOnError.items + pending)
                        productNameById = resolveProductNames(ordered)
                        totalCount = cachedOnError.total + pendingTotalCount
                        setAllItemsAndApplyFilters(ordered)
                        updatePageInfo(ordered.size, pending.size)
                        if (withSnack && !cacheNoticeShownInOfflineSession) {
                            postLoadingNotice = { showStockCacheNoticeOnce() }
                        } else {
                            showStockCacheNoticeOnce()
                        }
                    } else {
                        val pending = pendingStocksForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedRemoteTotal,
                            filtersActive = filtersActive
                        )
                        val ordered = markPendingDeletedStocks(pending)
                        productNameById = resolveProductNames(ordered)
                        totalCount = cachedRemoteTotal + pendingTotalCount
                        setAllItemsAndApplyFilters(ordered)
                        updatePageInfo(ordered.size, ordered.size)
                    }
                    if (res.code() == 403) {
                        UiNotifier.showBlocking(
                            this@StockActivity,
                            "Permisos insuficientes",
                            "No tienes permisos para ver stock.",
                            com.example.inventoryapp.R.drawable.ic_lock
                        )
                    } else {
                        snack.showError("? Error ${res.code()}")
                    }
                }
            } catch (e: Exception) {
                val filtersActive = hasActiveFilters()
                val effectiveOffset = if (filtersActive) 0 else currentOffset
                val effectiveLimit = if (filtersActive) 100 else pageSize
                val pendingTotalCount = pendingStocksCount()
                val cachedRemoteTotal = resolveCachedRemoteTotal(effectiveLimit)
                val cacheKey = CacheKeys.list(
                    "stocks",
                    mapOf(
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cachedOnError = cacheStore.get(cacheKey, StockListResponseDto::class.java)
                if (cachedOnError != null) {
                    val pending = pendingStocksForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedOnError.total,
                        filtersActive = filtersActive
                    )
                    val ordered = markPendingDeletedStocks(cachedOnError.items + pending)
                    productNameById = resolveProductNames(ordered)
                    totalCount = cachedOnError.total + pendingTotalCount
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pending.size)
                    if (withSnack && !cacheNoticeShownInOfflineSession) {
                        postLoadingNotice = { showStockCacheNoticeOnce() }
                    } else {
                        showStockCacheNoticeOnce()
                    }
                } else {
                    val pending = pendingStocksForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedRemoteTotal,
                        filtersActive = filtersActive
                    )
                    val ordered = markPendingDeletedStocks(pending)
                    productNameById = resolveProductNames(ordered)
                    totalCount = cachedRemoteTotal + pendingTotalCount
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, ordered.size)
                    if (e is IOException) {
                    } else {
                    }
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

    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        if (hasActiveFilters()) {
            val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
            val currentPage = if (filteredItems.isEmpty()) 0 else (filteredOffset / pageSize) + 1
            val totalPages = if (filteredItems.isEmpty()) 0 else ((filteredItems.size + pageSize - 1) / pageSize)
            binding.tvStockPageNumber.text = "Pagina $currentPage/$totalPages"
            binding.tvStockPageInfo.text = "Mostrando $shown/${filteredItems.size}"
            val prevEnabled = filteredOffset > 0
            val nextEnabled = shown < filteredItems.size
            binding.btnPrevPage.isEnabled = prevEnabled
            binding.btnNextPage.isEnabled = nextEnabled
            applyPagerButtonStyle(binding.btnPrevPage, prevEnabled)
            applyPagerButtonStyle(binding.btnNextPage, nextEnabled)
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
        binding.tvStockPageNumber.text = "Pagina $currentPage/$totalPages"
        binding.tvStockPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shownOnline < totalItems
        binding.btnPrevPage.isEnabled = prevEnabled
        binding.btnNextPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextPage, nextEnabled)
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
            binding.btnRefresh.setColorFilter(blue)
        } else {
            binding.btnRefresh.clearColorFilter()
        }
    }

    private suspend fun resolveProductNames(stocks: List<StockResponseDto>): Map<Int, String> {
        val ids = stocks.map { it.productId }.distinct()
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
        val productRes = runCatching {
            NetworkModule.api.listProducts(
                orderBy = "id",
                orderDir = "asc",
                limit = 100,
                offset = 0
            )
        }.getOrNull()
        if (productRes?.isSuccessful == true && productRes.body() != null) {
            val items = productRes.body()!!.items
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

    private fun createStock(
        productInputRaw: String,
        locationRaw: String,
        quantityRaw: String,
        onFinished: () -> Unit = {}
    ) {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear stock.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            onFinished()
            return
        }

        val productInput = productInputRaw.trim()
        val productId = productInput.toIntOrNull() ?: resolveProductIdByName(productInput)
        val location = normalizeLocationInput(locationRaw.trim())
        val quantity = quantityRaw.toIntOrNull()

        if (productId == null || location.isBlank() || quantity == null || quantity < 0) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se pudo crear stock",
                details = when {
                    productId == null -> "Product ID o nombre valido"
                    location.isBlank() -> "Location requerida"
                    else -> "Quantity >= 0"
                }
            )
            onFinished()
            return
        }

        val dto = StockCreateDto(productId = productId, location = location, quantity = quantity)

        val loading = CreateUiFeedback.showLoading(this, "stock")

        lifecycleScope.launch {
            var loadingHandled = false
            try {
                val res = NetworkModule.api.createStock(dto)
                if (res.isSuccessful) {
                    val created = res.body()
                    val name = productNameById[productId] ?: productId.toString()
                    val details = if (created != null) {
                        "ID: ${created.id}\nProducto: $name\nUbicacion: ${created.location}\nCantidad: ${created.quantity}"
                    } else {
                        "Producto: $name\nUbicacion: $location\nCantidad: $quantity"
                    }
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showCreatedPopup(this@StockActivity, "Stock creado", details)
                    }
                    createQuantityInput = ""
                    cacheStore.invalidatePrefix("stocks")
                    loadStocks()
                } else {
                    val detail = runCatching { res.errorBody()?.string() }.getOrNull()
                    val technical = canSeeTechnicalErrors()
                    val formatted = formatCreateStockError(res.code(), detail, technical)
                    val details = buildString {
                        append(formatted)
                        if (technical && res.code() > 0) append("\nHTTP ${res.code()}")
                    }
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showErrorPopup(
                            activity = this@StockActivity,
                            title = "No se pudo crear stock",
                            details = details
                        )
                    }
                }

            } catch (e: IOException) {
                OfflineQueue(this@StockActivity).enqueue(PendingType.STOCK_CREATE, gson.toJson(dto))
                val name = productNameById[productId] ?: productId.toString()
                loadingHandled = true
                loading.dismissThen {
                    CreateUiFeedback.showCreatedPopup(
                        this@StockActivity,
                        "Stock creado (offline)",
                        "Producto: $name\nUbicacion: $location\nCantidad: $quantity (offline)",
                        accentColorRes = R.color.offline_text
                    )
                }
                loadStocks()

            } catch (e: Exception) {
                loadingHandled = true
                val details = if (canSeeTechnicalErrors()) {
                    "Ha ocurrido un error inesperado\n${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}"
                } else {
                    "Ha ocurrido un error inesperado"
                }
                loading.dismissThen {
                    CreateUiFeedback.showErrorPopup(
                        activity = this@StockActivity,
                        title = "No se pudo crear stock",
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

    private fun showEditDialog(stock: StockResponseDto) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_stock, null)
        val title = view.findViewById<android.widget.TextView>(R.id.tvEditStockTitle)
        val meta = view.findViewById<android.widget.TextView>(R.id.tvEditStockMeta)
        val qtyInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditStockQty)
        val btnSave = view.findViewById<android.widget.Button>(R.id.btnEditStockSave)
        val btnDelete = view.findViewById<android.widget.Button>(R.id.btnEditStockDelete)
        val btnClose = view.findViewById<android.widget.ImageButton>(R.id.btnEditStockClose)

        val productName = productNameById[stock.productId] ?: "Producto ${stock.productId}"
        title.text = "Editar: $productName (ID ${stock.id})"
        meta.text = "Ubicacion: ${stock.location}"
        qtyInput.setText(stock.quantity.toString())

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnSave.setOnClickListener {
            val newQty = qtyInput.text?.toString()?.toIntOrNull()
            if (newQty == null || newQty < 0) {
                qtyInput.error = "Cantidad invalida"
                return@setOnClickListener
            }
            updateStock(stock.id, StockUpdateDto(quantity = newQty))
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            CreateUiFeedback.showQuestionConfirmDialog(
                activity = this,
                title = "Vaciar stock",
                message = "Se pondrá la cantidad a 0. ¿Continuar?",
                confirmText = "Vaciar",
                cancelText = "Cancelar"
            ) {
                updateStock(stock.id, StockUpdateDto(quantity = 0))
                dialog.dismiss()
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateStock(stockId: Int, body: StockUpdateDto) {

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateStock(stockId, body)
                if (res.isSuccessful) {
                    cacheStore.invalidatePrefix("stocks")
                    loadStocks()
                } else {                    if (res.code() == 403) {
                        UiNotifier.showBlocking(
                            this@StockActivity,
                            "Permisos insuficientes",
                            "No tienes permisos para actualizar stock.",
                            com.example.inventoryapp.R.drawable.ic_lock
                        )
                    } else {
                        snack.showError("? Error ${res.code()}: ${res.errorBody()?.string()}")
                    }
                }

            } catch (e: IOException) {
                val payload = OfflineSyncer.StockUpdatePayload(stockId, body)
                OfflineQueue(this@StockActivity).enqueue(PendingType.STOCK_UPDATE, gson.toJson(payload))
                snack.showQueuedOffline("Sin conexion. Update guardado offline")

            } catch (e: Exception) {
                snack.showError("âŒ Error red: ${e.message}")
            }
        }
    }

    private fun buildPendingStocks(): List<StockResponseDto> {
        val pending = OfflineQueue(this).getAll().filter { it.type == PendingType.STOCK_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, StockCreateDto::class.java) }.getOrNull()
            if (dto == null) {
                StockResponseDto(
                    productId = 0,
                    location = "offline",
                    quantity = 0,
                    id = -1 - index,
                    createdAt = "offline",
                    updatedAt = "offline"
                )
            } else {
                StockResponseDto(
                    productId = dto.productId,
                    location = dto.location,
                    quantity = dto.quantity,
                    id = -1 - index,
                    createdAt = "offline",
                    updatedAt = "offline"
                )
            }
        }
    }

    private fun showStockCacheNoticeOnce() {
        if (cacheNoticeShownInOfflineSession) return
        UiNotifier.showBlockingTimed(
            this,
            "Mostrando stock en cache y pendientes offline",
            R.drawable.sync,
            timeoutMs = 3_200L
        )
        cacheNoticeShownInOfflineSession = true
    }

    private fun pendingStocksCount(): Int {
        return OfflineQueue(this).getAll().count { it.type == PendingType.STOCK_CREATE }
    }

    private fun pendingStockDeleteIds(): Set<Int> {
        return OfflineQueue(this).getAll()
            .asSequence()
            .filter { it.type == PendingType.STOCK_UPDATE }
            .mapNotNull {
                runCatching {
                    gson.fromJson(it.payloadJson, OfflineSyncer.StockUpdatePayload::class.java)
                }.getOrNull()
            }
            .filter { it.body.quantity == 0 }
            .map { it.stockId }
            .toSet()
    }

    private fun markPendingDeletedStocks(rows: List<StockResponseDto>): List<StockResponseDto> {
        val pendingDeleteIds = pendingStockDeleteIds()
        if (pendingDeleteIds.isEmpty()) return rows
        return rows.map { stock ->
            if (stock.id > 0 && pendingDeleteIds.contains(stock.id)) {
                stock.copy(updatedAt = OFFLINE_DELETE_MARKER)
            } else {
                stock
            }
        }
    }

    private suspend fun resolveCachedRemoteTotal(limit: Int): Int {
        val keyAtStart = CacheKeys.list(
            "stocks",
            mapOf(
                "limit" to limit,
                "offset" to 0
            )
        )
        val cachedAtStart = cacheStore.get(keyAtStart, StockListResponseDto::class.java)
        if (cachedAtStart != null) return cachedAtStart.total

        if (limit != pageSize) {
            val keyDefault = CacheKeys.list(
                "stocks",
                mapOf(
                    "limit" to pageSize,
                    "offset" to 0
                )
            )
            val cachedDefault = cacheStore.get(keyDefault, StockListResponseDto::class.java)
            if (cachedDefault != null) return cachedDefault.total
        }
        return 0
    }

    private fun pendingStocksForPage(
        offset: Int,
        remoteTotal: Int,
        filtersActive: Boolean
    ): List<StockResponseDto> {
        val pendingAll = buildPendingStocks()
        if (pendingAll.isEmpty()) return emptyList()
        if (filtersActive) return pendingAll

        // Pending offline items are appended after the remote total and paged with the same page size.
        val startInPending = (offset - remoteTotal).coerceAtLeast(0)
        if (startInPending >= pendingAll.size) return emptyList()
        val endInPending = (offset + pageSize - remoteTotal)
            .coerceAtMost(pendingAll.size)
            .coerceAtLeast(startInPending)
        return pendingAll.subList(startInPending, endInPending)
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

    private fun bindLocationDropdown(auto: MaterialAutoCompleteTextView) {
        val adapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, locationDropdownValues)
        auto.setAdapter(adapter)
        auto.setOnClickListener { auto.showDropDown() }
        auto.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) auto.showDropDown() }
    }

    private fun openCreateStockDialog() {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear stock.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            return
        }
        if (createDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_stock_create_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCreateStockDialogClose)
        val btnCreate = view.findViewById<Button>(R.id.btnDialogCreateStock)
        val etProduct = view.findViewById<TextInputEditText>(R.id.etDialogCreateStockProduct)
        val etLocation = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogCreateStockLocation)
        val etQuantity = view.findViewById<TextInputEditText>(R.id.etDialogCreateStockQuantity)
        val tilLocation = view.findViewById<TextInputLayout>(R.id.tilDialogCreateStockLocation)

        etProduct.setText(createProductInput)
        etLocation.setText(createLocationInput, false)
        etQuantity.setText(createQuantityInput)

        bindLocationDropdown(etLocation)
        applyDialogDropdownStyle(listOf(tilLocation), listOf(etLocation))

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val product = etProduct.text?.toString().orEmpty()
            val location = etLocation.text?.toString().orEmpty()
            val quantity = etQuantity.text?.toString().orEmpty()

            createProductInput = product
            createLocationInput = location
            createQuantityInput = quantity

            btnCreate.isEnabled = false
            createStock(
                productInputRaw = product,
                locationRaw = location,
                quantityRaw = quantity,
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

    private fun openSearchStockDialog() {
        if (searchDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_stock_search_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnSearchStockDialogClose)
        val btnSearch = view.findViewById<Button>(R.id.btnDialogSearchStock)
        val btnClear = view.findViewById<Button>(R.id.btnDialogClearSearchStock)
        val etProduct = view.findViewById<TextInputEditText>(R.id.etDialogSearchStockProduct)
        val etLocation = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogSearchStockLocation)
        val etQuantity = view.findViewById<TextInputEditText>(R.id.etDialogSearchStockQuantity)
        val tilLocation = view.findViewById<TextInputLayout>(R.id.tilDialogSearchStockLocation)

        etProduct.setText(searchProductFilter)
        etLocation.setText(searchLocationFilter, false)
        etQuantity.setText(searchQuantityFilter)

        bindLocationDropdown(etLocation)
        applyDialogDropdownStyle(listOf(tilLocation), listOf(etLocation))

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnSearch.setOnClickListener {
            searchProductFilter = etProduct.text?.toString().orEmpty().trim()
            searchLocationFilter = etLocation.text?.toString().orEmpty().trim()
            searchQuantityFilter = etQuantity.text?.toString().orEmpty().trim()
            hideKeyboard()
            dialog.dismiss()
            applySearchFilters()
        }
        btnClear.setOnClickListener {
            etProduct.setText("")
            etLocation.setText("", false)
            etQuantity.setText("")
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

    private fun updateStocksListAdaptiveHeight() {
        binding.scrollStock.post {
            val topSpacerLp = binding.viewStockTopSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val bottomSpacerLp = binding.viewStockBottomSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val cardLp = binding.cardStockList.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val rvLp = binding.rvStocks.layoutParams as? LinearLayout.LayoutParams ?: return@post

            val visibleCount = if (::adapter.isInitialized) adapter.itemCount else 0
            if (visibleCount in 1..pageSize) {
                topSpacerLp.height = 0
                topSpacerLp.weight = 1f
                bottomSpacerLp.height = 0
                bottomSpacerLp.weight = 1f
                cardLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                cardLp.weight = 0f
                rvLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                rvLp.weight = 0f
                binding.rvStocks.isNestedScrollingEnabled = false
            } else {
                topSpacerLp.height = 0
                topSpacerLp.weight = 0f
                bottomSpacerLp.height = 0
                bottomSpacerLp.weight = 0f
                cardLp.height = 0
                cardLp.weight = 1f
                rvLp.height = 0
                rvLp.weight = 1f
                binding.rvStocks.isNestedScrollingEnabled = true
            }
            binding.viewStockTopSpacer.layoutParams = topSpacerLp
            binding.viewStockBottomSpacer.layoutParams = bottomSpacerLp
            binding.cardStockList.layoutParams = cardLp
            binding.rvStocks.layoutParams = rvLp
        }
    }

    private fun applyStockTitleGradient() {
        binding.tvStockTitle.post {
            val paint = binding.tvStockTitle.paint
            val width = paint.measureText(binding.tvStockTitle.text.toString())
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
            binding.tvStockTitle.invalidate()
        }
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true, showNotFoundDialog = true)
    }

    private fun applySearchFiltersInternal(
        allowReload: Boolean,
        showNotFoundDialog: Boolean = false
    ) {
        val productRaw = searchProductFilter.trim()
        val locationRaw = normalizeLocationInput(searchLocationFilter.trim())
        val qtyRaw = searchQuantityFilter.trim()

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > allItems.size)) {
            pendingFilterApply = true
            pendingSearchNotFoundDialog = showNotFoundDialog
            currentOffset = 0
            loadStocks()
            return
        }

        var filtered = allItems
        if (productRaw.isNotBlank()) {
            val productId = productRaw.toIntOrNull()
            filtered = if (productId != null) {
                filtered.filter { it.productId == productId }
            } else {
                val needle = productRaw.lowercase()
                filtered.filter { (productNameById[it.productId] ?: "").lowercase().contains(needle) }
            }
        }
        if (locationRaw.isNotBlank()) {
            val needle = locationRaw.lowercase()
            filtered = filtered.filter { it.location.lowercase().contains(needle) }
        }
        if (qtyRaw.isNotBlank()) {
            val qty = qtyRaw.toIntOrNull()
            if (qty != null) {
                filtered = filtered.filter { it.quantity == qty }
            }
        }

        filteredItems = filtered
        filteredOffset = 0
        applyFilteredPage()
        if (showNotFoundDialog && hasActiveFilters() && filtered.isEmpty()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se encontro stock",
                details = buildStockSearchNotFoundDetails(productRaw, locationRaw, qtyRaw),
                animationRes = R.raw.notfound
            )
        }
    }

    private fun clearSearchFilters() {
        searchProductFilter = ""
        searchLocationFilter = ""
        searchQuantityFilter = ""
        filteredItems = emptyList()
        filteredOffset = 0
        items = allItems
        adapter.submit(allItems, productNameById)
        updatePageInfo(items.size, items.size)
        updateStocksListAdaptiveHeight()
    }

    private fun setAllItemsAndApplyFilters(ordered: List<StockResponseDto>) {
        allItems = ordered
        if (hasActiveFilters() || pendingFilterApply) {
            applySearchFiltersInternal(allowReload = false)
        } else {
            items = ordered
            adapter.submit(ordered, productNameById)
            updateStocksListAdaptiveHeight()
        }
    }

    private fun hasActiveFilters(): Boolean {
        return searchProductFilter.isNotBlank() ||
            searchLocationFilter.isNotBlank() ||
            searchQuantityFilter.isNotBlank()
    }

    private fun buildStockSearchNotFoundDetails(
        productRaw: String,
        locationRaw: String,
        qtyRaw: String
    ): String {
        val parts = mutableListOf<String>()
        if (productRaw.isNotBlank()) {
            val productLabel = if (productRaw.toIntOrNull() != null) {
                "del producto ID $productRaw"
            } else {
                "del producto \"$productRaw\""
            }
            parts.add(productLabel)
        }
        if (locationRaw.isNotBlank()) {
            parts.add("en ubicacion \"$locationRaw\"")
        }
        if (qtyRaw.isNotBlank()) {
            parts.add("con cantidad $qtyRaw")
        }
        return if (parts.isEmpty()) {
            "No se encontro stock con los filtros actuales."
        } else {
            "No se encontro stock ${parts.joinToString(separator = " ")}."
        }
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        items = page
        adapter.submit(page, productNameById)
        updatePageInfo(page.size, page.size)
        updateStocksListAdaptiveHeight()
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun resolveProductIdByName(input: String): Int? {
        val needle = input.trim().lowercase()
        if (needle.isBlank()) return null
        val match = productNameById.entries.firstOrNull { it.value.lowercase() == needle }
        return match?.key
    }

    private fun formatCreateStockError(code: Int, detail: String?, technical: Boolean): String {
        val backendMsg = extractBackendErrorMessage(detail)
        val detailLower = backendMsg.lowercase()
        val mapped = when (code) {
            400 -> "Datos invalidos para crear stock"
            409 -> {
                if (
                    detailLower.contains("location") ||
                    detailLower.contains("ubicaci") ||
                    detailLower.contains("already exists") ||
                    detailLower.contains("ya existe")
                ) {
                    "Ya existe stock para ese producto en esa ubicacion"
                } else {
                    "Conflicto: ya existe un stock similar"
                }
            }
            422 -> "No se puede crear stock con esos datos"
            else -> ApiErrorFormatter.format(code, backendMsg)
        }

        if (code == 400 && isDuplicateStockMessage(detailLower)) {
            return "Ya existe stock para ese producto en esa ubicacion"
        }
        if (technical && backendMsg.isNotBlank() && !isTooGenericBackendMessage(backendMsg)) {
            return backendMsg
        }
        return mapped
    }

    private fun isDuplicateStockMessage(messageLower: String): Boolean {
        if (messageLower.isBlank()) return false
        return (
            (messageLower.contains("stock") && messageLower.contains("exist")) ||
            (messageLower.contains("stock") && messageLower.contains("duplic")) ||
            (messageLower.contains("producto") && messageLower.contains("ubicaci") && messageLower.contains("exist")) ||
            messageLower.contains("unique constraint") ||
            messageLower.contains("integrityerror")
        )
    }

    private fun isTooGenericBackendMessage(message: String): Boolean {
        val m = message.trim().lowercase()
        return m.isBlank() ||
            m == "bad request" ||
            m == "invalid request" ||
            m == "validation error" ||
            m == "error"
    }

    private fun extractBackendErrorMessage(detail: String?): String {
        if (detail.isNullOrBlank()) return ""

        val raw = detail.trim()
        val fromJsonObject = runCatching {
            val obj = JSONObject(raw)
            obj.optString("detail").takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
                ?: parseErrorsNode(obj.opt("errors"))
        }.getOrNull()
        if (!fromJsonObject.isNullOrBlank()) return fromJsonObject

        val fromJsonArray = runCatching {
            val arr = JSONArray(raw)
            if (arr.length() > 0) arr.optString(0) else null
        }.getOrNull()
        if (!fromJsonArray.isNullOrBlank()) return fromJsonArray

        return raw.take(240)
    }

    private fun canSeeTechnicalErrors(): Boolean {
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val role = prefs.getString("cached_role", null) ?: return false
        return role.equals("ADMIN", ignoreCase = true) ||
            role.equals("MANAGER", ignoreCase = true)
    }

    private fun isUserRole(): Boolean {
        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        return role.equals("USER", ignoreCase = true)
    }

    private fun parseErrorsNode(node: Any?): String? {
        return when (node) {
            is JSONObject -> {
                val keys = node.keys()
                val parts = mutableListOf<String>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    val valueText = when (value) {
                        is JSONArray -> (0 until value.length())
                            .mapNotNull { idx -> value.optString(idx).takeIf { it.isNotBlank() } }
                            .joinToString(", ")
                        else -> value?.toString().orEmpty()
                    }
                    if (valueText.isNotBlank()) {
                        parts.add("$key: $valueText")
                    }
                }
                parts.joinToString(" | ").ifBlank { null }
            }
            is JSONArray -> (0 until node.length())
                .mapNotNull { idx -> node.optString(idx).takeIf { it.isNotBlank() } }
                .joinToString(", ")
                .ifBlank { null }
            else -> null
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
