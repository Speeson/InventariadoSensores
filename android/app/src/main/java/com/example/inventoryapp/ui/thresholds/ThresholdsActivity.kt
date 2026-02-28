package com.example.inventoryapp.ui.thresholds
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.cache.CacheKeys
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ThresholdCreateDto
import com.example.inventoryapp.data.remote.model.ThresholdListResponseDto
import com.example.inventoryapp.data.remote.model.ThresholdResponseDto
import com.example.inventoryapp.data.remote.model.ThresholdUpdateDto
import com.example.inventoryapp.databinding.ActivityThresholdsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.TopCenterActionHost
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R
import android.widget.ArrayAdapter
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import android.view.View
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button

class ThresholdsActivity : AppCompatActivity(), TopCenterActionHost {
    companion object {
        @Volatile
        private var cacheNoticeShownInOfflineSession = false
    }

    private lateinit var binding: ActivityThresholdsBinding
    private lateinit var adapter: ThresholdListAdapter
    private lateinit var cacheStore: CacheStore
    private var items: List<ThresholdResponseDto> = emptyList()
    private val gson = Gson()
    private var productNameById: Map<Int, String> = emptyMap()
    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var filteredItems: List<ThresholdResponseDto> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false
    private var pendingSearchNotFoundDialog = false
    private var bulkProductNamesCache: Map<Int, String>? = null
    private var bulkProductNamesCacheAtMs: Long = 0L
    private val bulkProductNamesCacheTtlMs = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateThresholdAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivSearchThreshold, R.drawable.search)
        applyThresholdTitleGradient()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        cacheStore = CacheStore.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = ThresholdListAdapter { threshold ->
            showEditDialog(threshold)
        }
        binding.rvThresholds.layoutManager = LinearLayoutManager(this)
        binding.rvThresholds.adapter = adapter
        lifecycleScope.launch {
            NetworkModule.offlineState.collectLatest { offline ->
                if (!offline) {
                    cacheNoticeShownInOfflineSession = false
                }
            }
        }

        binding.btnCreate.setOnClickListener { createThreshold() }
        binding.btnRefresh.setOnClickListener {
            invalidateBulkProductNamesCache()
            loadThresholds(withSnack = true)
        }
        binding.layoutCreateThresholdHeader.setOnClickListener { toggleCreateForm() }
        binding.layoutSearchThresholdHeader.setOnClickListener { toggleSearchForm() }
        binding.btnSearch.setOnClickListener {
            hideKeyboard()
            applySearchFilters()
        }
        binding.btnClear.setOnClickListener {
            hideKeyboard()
            clearSearchFilters()
        }

        setupLocationDropdowns()
        binding.tilCreateLocation.post { applyThresholdDropdownIcons() }

        binding.btnPrevThresholdPage.setOnClickListener {
            if (hasActiveFilters()) {
                if (filteredOffset <= 0) return@setOnClickListener
                filteredOffset = (filteredOffset - pageSize).coerceAtLeast(0)
                applyFilteredPage()
                binding.rvThresholds.scrollToPosition(0)
                return@setOnClickListener
            }
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadThresholds()
            binding.rvThresholds.scrollToPosition(0)
        }
        binding.btnNextThresholdPage.setOnClickListener {
            if (hasActiveFilters()) {
                val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
                if (shown >= filteredItems.size) return@setOnClickListener
                filteredOffset += pageSize
                applyFilteredPage()
                binding.rvThresholds.scrollToPosition(0)
                return@setOnClickListener
            }
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadThresholds()
            binding.rvThresholds.scrollToPosition(0)
        }

        applyPagerButtonStyle(binding.btnPrevThresholdPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextThresholdPage, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadThresholds()
    }

    override fun onTopCreateAction() {
        if (binding.layoutCreateThresholdContent.visibility != View.VISIBLE) {
            toggleCreateForm()
        }
    }

    override fun onTopFilterAction() {
        if (binding.layoutSearchThresholdContent.visibility != View.VISIBLE) {
            toggleSearchForm()
        }
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true, showNotFoundDialog = true)
    }

    private fun applySearchFiltersInternal(
        allowReload: Boolean,
        showNotFoundDialog: Boolean = false
    ) {
        val productRaw = binding.etSearchProductId.text.toString().trim()
        val locationRaw = normalizeLocationInput(binding.etSearchLocation.text.toString().trim())

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > items.size)) {
            pendingFilterApply = true
            pendingSearchNotFoundDialog = showNotFoundDialog
            currentOffset = 0
            loadThresholds()
            return
        }

        var filtered = items
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
            filtered = filtered.filter { (it.location ?: "").lowercase().contains(needle) }
        }

        filteredItems = filtered
        filteredOffset = 0
        applyFilteredPage()
        if (showNotFoundDialog && hasActiveFilters() && filtered.isEmpty()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se encontraron thresholds",
                details = buildThresholdSearchNotFoundDetails(productRaw, locationRaw),
                animationRes = R.raw.notfound
            )
        }
    }

    private fun clearSearchFilters() {
        binding.etSearchProductId.setText("")
        binding.etSearchLocation.setText("")
        filteredItems = emptyList()
        filteredOffset = 0
        val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
        adapter.submit(rows)
        updatePageInfo(rows.size)
    }

    private fun loadThresholds(productId: Int? = null, location: String? = null, withSnack: Boolean = false) {
        if (isLoading) return
        isLoading = true
        var postLoadingNotice: (() -> Unit)? = null
        val loading = if (withSnack) {
            CreateUiFeedback.showListLoading(
                this,
                message = "Cargando thresholds",
                animationRes = R.raw.glass_loading_list,
                minCycles = 2
            )
        } else {
            null
        }
        lifecycleScope.launch {
            try {
                val filtersActive = hasActiveFilters()
                val effectiveLimit = if (filtersActive) 200 else pageSize
                val effectiveOffset = if (filtersActive) 0 else currentOffset
                if (filtersActive) currentOffset = 0
                val pendingAll = buildPendingThresholds()
                val pendingTotalCount = pendingAll.size
                val cachedRemoteTotal = resolveCachedThresholdsRemoteTotal(productId, location, effectiveLimit)
                val cacheKey = CacheKeys.list(
                    "thresholds",
                    mapOf(
                        "product_id" to productId,
                        "location" to location,
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cached = cacheStore.get(cacheKey, ThresholdListResponseDto::class.java)
                if (cached != null) {
                    val pending = pendingThresholdsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cached.total,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    items = cached.items + pending
                    totalCount = cached.total + pendingTotalCount
                    productNameById = fetchProductNames(items.map { it.productId }.toSet())
                    val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
                    adapter.submit(rows)
                    updatePageInfo(rows.size)
                    isLoading = false
                }
                val res = NetworkModule.api.listThresholds(productId = productId, location = location, limit = effectiveLimit, offset = effectiveOffset)
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful && res.body() != null) {
                    cacheStore.put(cacheKey, res.body()!!)
                    val pending = pendingThresholdsForPage(
                        offset = effectiveOffset,
                        remoteTotal = res.body()!!.total,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    items = res.body()!!.items + pending
                    totalCount = res.body()!!.total + pendingTotalCount
                    productNameById = fetchProductNames(items.map { it.productId }.toSet())
                    val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
                    if (hasActiveFilters() || pendingFilterApply) {
                        applySearchFiltersInternal(allowReload = false)
                    } else {
                        adapter.submit(rows)
                        updatePageInfo(rows.size)
                    }
                    if (withSnack) {
                        postLoadingNotice = {
                            CreateUiFeedback.showStatusPopup(
                                activity = this@ThresholdsActivity,
                                title = "Thresholds cargados",
                                details = "Se han cargado correctamente.",
                                animationRes = R.raw.correct_create,
                                autoDismissMs = 2500L
                            )
                        }
                    }
                } else {
                    val suppressInvalidSearchMessage = hasActiveFilters() && (res.code() == 400 || res.code() == 422)
                    if (!suppressInvalidSearchMessage) {
                        UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code()))
                    }
                    val cachedOnError = cacheStore.get(cacheKey, ThresholdListResponseDto::class.java)
                    if (cachedOnError != null) {
                        val pending = pendingThresholdsForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedOnError.total,
                            filtersActive = filtersActive,
                            pendingAll = pendingAll
                        )
                        items = cachedOnError.items + pending
                        totalCount = cachedOnError.total + pendingTotalCount
                        productNameById = fetchProductNames(items.map { it.productId }.toSet())
                        val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
                        adapter.submit(rows)
                        updatePageInfo(rows.size)
                        if (withSnack && !cacheNoticeShownInOfflineSession) {
                            postLoadingNotice = { showThresholdsCacheNoticeOnce() }
                        } else {
                            showThresholdsCacheNoticeOnce()
                        }
                    }
                }
            } catch (e: Exception) {
                val cacheKey = CacheKeys.list(
                    "thresholds",
                    mapOf(
                        "product_id" to productId,
                        "location" to location,
                        "limit" to if (hasActiveFilters()) 200 else pageSize,
                        "offset" to if (hasActiveFilters()) 0 else currentOffset
                    )
                )
                val cachedOnError = cacheStore.get(cacheKey, ThresholdListResponseDto::class.java)
                if (cachedOnError != null) {
                    val filtersActive = hasActiveFilters()
                    val pendingAll = buildPendingThresholds()
                    val pendingTotalCount = pendingAll.size
                    val effectiveOffset = if (filtersActive) 0 else currentOffset
                    val pending = pendingThresholdsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedOnError.total,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    items = cachedOnError.items + pending
                    totalCount = cachedOnError.total + pendingTotalCount
                    productNameById = fetchProductNames(items.map { it.productId }.toSet())
                    val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
                    adapter.submit(rows)
                    updatePageInfo(rows.size)
                    if (withSnack && !cacheNoticeShownInOfflineSession) {
                        postLoadingNotice = { showThresholdsCacheNoticeOnce() }
                    } else {
                        showThresholdsCacheNoticeOnce()
                    }
                } else {
                    val filtersActive = hasActiveFilters()
                    val effectiveOffset = if (filtersActive) 0 else currentOffset
                    val effectiveLimit = if (filtersActive) 200 else pageSize
                    val pendingAll = buildPendingThresholds()
                    val pendingTotalCount = pendingAll.size
                    val cachedRemoteTotal = resolveCachedThresholdsRemoteTotal(productId, location, effectiveLimit)
                    val pending = pendingThresholdsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedRemoteTotal,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    productNameById = fetchProductNames(pending.map { it.productId }.toSet())
                    val rows = pending.map { ThresholdRowUi(it, productNameById[it.productId]) }
                    adapter.submit(rows)
                    totalCount = cachedRemoteTotal + pendingTotalCount
                    updatePageInfo(rows.size)
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

    private fun createThreshold() {
        val productInput = binding.etProductId.text.toString().trim()
        val productId = productInput.toIntOrNull() ?: resolveProductIdByName(productInput)
        val location = normalizeLocationInput(binding.etLocation.text.toString().trim()).ifBlank { null }
        val minQty = binding.etMinQty.text.toString().trim().toIntOrNull()

        if (productId == null) { binding.etProductId.error = "Producto ID o nombre valido"; return }
        if (minQty == null || minQty < 0) { binding.etMinQty.error = "Min >= 0"; return }

        val dto = ThresholdCreateDto(productId = productId, location = location, minQuantity = minQty)
        binding.btnCreate.isEnabled = false
        val loading = CreateUiFeedback.showLoading(this, "threshold")
        lifecycleScope.launch {
            var loadingHandled = false
            try {
                val res = NetworkModule.api.createThreshold(dto)
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful) {
                    val created = res.body()
                    val productName = productNameById[productId] ?: "Producto $productId"
                    val details = if (created != null) {
                        "ID: ${created.id}\nProducto: $productName\nUbicacion: ${created.location ?: "-"}\nUmbral: ${created.minQuantity}"
                    } else {
                        "Producto: $productName\nUbicacion: ${location ?: "-"}\nUmbral: $minQty"
                    }
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showCreatedPopup(
                            this@ThresholdsActivity,
                            "Threshold creado",
                            details
                        )
                    }
                    binding.etProductId.setText("")
                    binding.etLocation.setText("")
                    binding.etMinQty.setText("")
                    cacheStore.invalidatePrefix("thresholds")
                    loadThresholds()
                } else {
                    val raw = runCatching { res.errorBody()?.string() }.getOrNull()
                    val details = formatCreateThresholdError(res.code(), raw)
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showErrorPopup(
                            activity = this@ThresholdsActivity,
                            title = "No se pudo crear threshold",
                            details = details
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    OfflineQueue(this@ThresholdsActivity).enqueue(PendingType.THRESHOLD_CREATE, gson.toJson(dto))
                    val productName = productNameById[productId] ?: "Producto $productId"
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showCreatedPopup(
                            this@ThresholdsActivity,
                            "Threshold creado (offline)",
                            "Producto: $productName\nUbicacion: ${location ?: "-"}\nUmbral: $minQty (offline)",
                            accentColorRes = R.color.offline_text
                        )
                    }
                    loadThresholds()
                } else {
                    val details = "Ha ocurrido un error inesperado"
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showErrorPopup(
                            activity = this@ThresholdsActivity,
                            title = "No se pudo crear threshold",
                            details = details
                        )
                    }
                }
            } finally {
                if (!loadingHandled) {
                    loading.dismiss()
                }
                binding.btnCreate.isEnabled = true
            }
        }
    }

    private fun showEditDialog(threshold: ThresholdResponseDto) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_threshold, null)
        val title = view.findViewById<android.widget.TextView>(R.id.tvEditThresholdTitle)
        val meta = view.findViewById<android.widget.TextView>(R.id.tvEditThresholdMeta)
        val inputLocation = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditThresholdLocation)
        val inputMin = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditThresholdMinQty)
        val btnSave = view.findViewById<android.widget.Button>(R.id.btnEditThresholdSave)
        val btnDelete = view.findViewById<android.widget.Button>(R.id.btnEditThresholdDelete)
        val btnClose = view.findViewById<android.widget.ImageButton>(R.id.btnEditThresholdClose)

        title.text = "Editar threshold #${threshold.id}"
        meta.text = "Producto ID: ${threshold.productId}"
        inputLocation.setText(threshold.location ?: "")
        inputMin.setText(threshold.minQuantity.toString())

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnSave.setOnClickListener {
            val newLoc = inputLocation.text?.toString()?.trim().orEmpty().ifBlank { null }
            val newMin = inputMin.text?.toString()?.trim()?.toIntOrNull()
            if (newMin == null || newMin < 0) {
                inputMin.error = "Min >= 0"
                return@setOnClickListener
            }
            updateThreshold(threshold.id, newLoc, newMin)
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            CreateUiFeedback.showQuestionConfirmDialog(
                activity = this,
                title = "Eliminar threshold",
                message = "Se eliminará el threshold #${threshold.id}. ¿Continuar?",
                confirmText = "Eliminar",
                cancelText = "Cancelar"
            ) {
                deleteThreshold(threshold.id)
                dialog.dismiss()
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateThreshold(id: Int, location: String?, minQty: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateThreshold(
                    id,
                    ThresholdUpdateDto(location = location, minQuantity = minQty)
                )
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful) {
                    cacheStore.invalidatePrefix("thresholds")
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                } else {
                }
            }
        }
    }

    private fun deleteThreshold(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.deleteThreshold(id)
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful) {
                    cacheStore.invalidatePrefix("thresholds")
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                } else {
                }
            }
        }
    }

    private fun buildPendingThresholds(): List<ThresholdResponseDto> {
        val pending = OfflineQueue(this).getAll().filter { it.type == PendingType.THRESHOLD_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, ThresholdCreateDto::class.java) }.getOrNull()
            ThresholdResponseDto(
                id = -1 - index,
                productId = dto?.productId ?: 0,
                locationId = null,
                location = dto?.location,
                minQuantity = dto?.minQuantity ?: 0,
                createdAt = "offline",
                updatedAt = null
            )
        }
    }

    private fun pendingThresholdsForPage(
        offset: Int,
        remoteTotal: Int,
        filtersActive: Boolean,
        pendingAll: List<ThresholdResponseDto>
    ): List<ThresholdResponseDto> {
        if (pendingAll.isEmpty()) return emptyList()
        if (filtersActive) return pendingAll
        val startInPending = (offset - remoteTotal).coerceAtLeast(0)
        if (startInPending >= pendingAll.size) return emptyList()
        val endInPending = (offset + pageSize - remoteTotal)
            .coerceAtMost(pendingAll.size)
            .coerceAtLeast(startInPending)
        return pendingAll.subList(startInPending, endInPending)
    }

    private suspend fun resolveCachedThresholdsRemoteTotal(
        productId: Int?,
        location: String?,
        effectiveLimit: Int
    ): Int {
        val keyAtStart = CacheKeys.list(
            "thresholds",
            mapOf(
                "product_id" to productId,
                "location" to location,
                "limit" to effectiveLimit,
                "offset" to 0
            )
        )
        val cachedAtStart = cacheStore.get(keyAtStart, ThresholdListResponseDto::class.java)
        if (cachedAtStart != null) return cachedAtStart.total
        if (effectiveLimit != pageSize) {
            val keyDefault = CacheKeys.list(
                "thresholds",
                mapOf(
                    "product_id" to productId,
                    "location" to location,
                    "limit" to pageSize,
                    "offset" to 0
                )
            )
            val cachedDefault = cacheStore.get(keyDefault, ThresholdListResponseDto::class.java)
            if (cachedDefault != null) return cachedDefault.total
        }
        return 0
    }

    private suspend fun fetchProductNames(ids: Set<Int>): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        val bulkNames = getOrFetchBulkProductNames()
        ids.forEach { id ->
            bulkNames[id]?.let { out[id] = it }
        }
        ids.forEach { id ->
            if (out.containsKey(id)) return@forEach
            try {
                val res = NetworkModule.api.getProduct(id)
                if (res.isSuccessful && res.body() != null) {
                    out[id] = res.body()!!.name
                }
            } catch (_: Exception) {
                // Keep fallback labels if lookup fails.
            }
        }
        return out
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
            val map = listRes.body()!!.items.associate { it.id to it.name }
            bulkProductNamesCache = map
            bulkProductNamesCacheAtMs = now
            return map
        }
        return emptyMap()
    }

    private fun invalidateBulkProductNamesCache() {
        bulkProductNamesCache = null
        bulkProductNamesCacheAtMs = 0L
    }

    private fun resolveProductIdByName(input: String): Int? {
        val needle = input.trim().lowercase()
        if (needle.isBlank()) return null
        val match = productNameById.entries.firstOrNull { it.value.lowercase() == needle }
        return match?.key
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun showThresholdsCacheNoticeOnce() {
        if (cacheNoticeShownInOfflineSession) return
        UiNotifier.showBlockingTimed(
            this,
            "Mostrando thresholds en cache y pendientes offline",
            R.drawable.sync,
            timeoutMs = 3_200L
        )
        cacheNoticeShownInOfflineSession = true
    }

    private fun hasActiveFilters(): Boolean {
        return binding.etSearchProductId.text?.isNotBlank() == true ||
            binding.etSearchLocation.text?.isNotBlank() == true
    }

    private fun buildThresholdSearchNotFoundDetails(productRaw: String, locationRaw: String): String {
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
        return if (parts.isEmpty()) {
            "No se encontraron thresholds con los filtros actuales."
        } else {
            "No se encontraron thresholds ${parts.joinToString(separator = " ")}."
        }
    }

    private fun canShowTechnicalThresholdErrors(): Boolean {
        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        return role.equals("ADMIN", ignoreCase = true) || role.equals("MANAGER", ignoreCase = true)
    }

    private fun formatCreateThresholdError(code: Int, rawError: String?): String {
        val raw = rawError?.trim().orEmpty()
        val normalized = raw.lowercase()
        val technical = canShowTechnicalThresholdErrors()

        val productNotFound =
            (normalized.contains("product") || normalized.contains("producto")) &&
                (normalized.contains("not found") || normalized.contains("no existe") || normalized.contains("inexist"))
        val locationInvalid =
            (normalized.contains("location") || normalized.contains("ubic")) &&
                (normalized.contains("invalid") || normalized.contains("not found") || normalized.contains("no existe"))
        val duplicateThreshold =
            (normalized.contains("threshold") || normalized.contains("umbral")) &&
                (normalized.contains("exists") || normalized.contains("existe") || normalized.contains("duplic"))
        val invalidMinQty =
            (normalized.contains("min") || normalized.contains("quantity") || normalized.contains("cantidad")) &&
                (normalized.contains("invalid") || normalized.contains(">= 0") || normalized.contains("must be"))

        if (productNotFound) {
            return if (technical) {
                buildString {
                    append("No se puede crear: el producto indicado no existe.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactThresholdErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "No se puede crear el threshold porque el producto no existe."
            }
        }

        if (locationInvalid) {
            return if (technical) {
                buildString {
                    append("No se puede crear: la ubicacion indicada no es valida.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactThresholdErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "No se puede crear el threshold porque la ubicacion no es valida."
            }
        }

        if (duplicateThreshold) {
            return if (technical) {
                buildString {
                    append("No se puede crear: ya existe un threshold para ese producto/ubicacion.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactThresholdErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "Ya existe un threshold para ese producto y ubicacion."
            }
        }

        if (invalidMinQty) {
            return if (technical) {
                buildString {
                    append("No se puede crear: valor de umbral invalido.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactThresholdErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "El valor del umbral no es vÃ¡lido."
            }
        }

        return if (technical) {
            buildString {
                append(
                    when (code) {
                        400, 422 -> "Datos invalidos para crear threshold."
                        403 -> "No tienes permisos para crear thresholds."
                        409 -> "Conflicto al crear threshold."
                        500 -> "Error interno del servidor al crear threshold."
                        else -> ApiErrorFormatter.format(code, raw)
                    }
                )
                if (raw.isNotBlank() && code !in setOf(400, 422, 403, 409, 500)) {
                    append("\nDetalle: ${compactThresholdErrorDetail(raw)}")
                }
                if (code > 0) append("\nHTTP $code")
            }
        } else {
            when (code) {
                400, 422 -> "No se puede crear el threshold. Revisa los datos introducidos."
                403 -> "No tienes permisos para crear thresholds."
                409 -> "No se puede crear el threshold porque entra en conflicto con otro existente."
                500 -> "No se puede crear el threshold por un problema del servidor."
                else -> "No se pudo crear el threshold. Intentalo de nuevo."
            }
        }
    }

    private fun compactThresholdErrorDetail(raw: String, maxLen: Int = 180): String {
        val singleLine = raw.replace("\\s+".toRegex(), " ").trim()
        return if (singleLine.length <= maxLen) singleLine else singleLine.take(maxLen) + "..."
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        val rows = page.map { ThresholdRowUi(it, productNameById[it.productId]) }
        adapter.submit(rows)
        updatePageInfo(rows.size)
    }

    private fun updatePageInfo(pageSizeLoaded: Int) {
        if (hasActiveFilters()) {
            val shown = (filteredOffset + pageSizeLoaded).coerceAtMost(filteredItems.size)
            binding.tvThresholdPageInfo.text = "Mostrando $shown/${filteredItems.size}"
            val prevEnabled = filteredOffset > 0
            val nextEnabled = shown < filteredItems.size
            binding.btnPrevThresholdPage.isEnabled = prevEnabled
            binding.btnNextThresholdPage.isEnabled = nextEnabled
            applyPagerButtonStyle(binding.btnPrevThresholdPage, prevEnabled)
            applyPagerButtonStyle(binding.btnNextThresholdPage, nextEnabled)
            return
        }
        val shown = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        val label = if (totalCount > 0) "Mostrando $shown/$totalCount" else "Mostrando ${pageSizeLoaded}/${pageSizeLoaded}"
        binding.tvThresholdPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shown < totalCount
        binding.btnPrevThresholdPage.isEnabled = prevEnabled
        binding.btnNextThresholdPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevThresholdPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextThresholdPage, nextEnabled)
    }

    private fun applyPagerButtonStyle(button: Button, enabled: Boolean) {
        button.backgroundTintList = null
        if (!enabled) {
            val colors = intArrayOf(
                ContextCompat.getColor(this, android.R.color.darker_gray),
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
            val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
                cornerRadius = resources.displayMetrics.density * 18f
                setStroke((resources.displayMetrics.density * 1f).toInt(), 0xFFB0B0B0.toInt())
            }
            button.background = drawable
            button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            return
        }
        val colors = intArrayOf(
            ContextCompat.getColor(this, R.color.icon_grad_start),
            ContextCompat.getColor(this, R.color.icon_grad_mid2),
            ContextCompat.getColor(this, R.color.icon_grad_mid1),
            ContextCompat.getColor(this, R.color.icon_grad_end)
        )
        val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = resources.displayMetrics.density * 18f
            setStroke((resources.displayMetrics.density * 1f).toInt(), 0x33000000)
        }
        button.background = drawable
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun toggleCreateForm() {
        TransitionManager.beginDelayedTransition(binding.scrollThresholds, AutoTransition().setDuration(180))
        val isVisible = binding.layoutCreateThresholdContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutCreateThresholdContent.visibility = View.GONE
            binding.layoutSearchThresholdContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutCreateThresholdContent.visibility = View.VISIBLE
            binding.layoutSearchThresholdContent.visibility = View.GONE
            setToggleActive(binding.layoutCreateThresholdHeader)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollThresholds, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchThresholdContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutSearchThresholdContent.visibility = View.GONE
            binding.layoutCreateThresholdContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutSearchThresholdContent.visibility = View.VISIBLE
            binding.layoutCreateThresholdContent.visibility = View.GONE
            setToggleActive(binding.layoutSearchThresholdHeader)
        }
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutCreateThresholdHeader) {
            binding.layoutCreateThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.layoutSearchThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        } else if (active === binding.layoutSearchThresholdHeader) {
            binding.layoutCreateThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutCreateThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        }
    }

    private fun setupLocationDropdowns() {
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
                    val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = ArrayAdapter(this@ThresholdsActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etLocation.setAdapter(adapter)
                    binding.etLocation.setOnClickListener { binding.etLocation.showDropDown() }
                    binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etLocation.showDropDown()
                    }
                    binding.etSearchLocation.setAdapter(adapter)
                    binding.etSearchLocation.setOnClickListener { binding.etSearchLocation.showDropDown() }
                    binding.etSearchLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etSearchLocation.showDropDown()
                    }
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
                val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                val adapter = ArrayAdapter(this@ThresholdsActivity, android.R.layout.simple_list_item_1, allValues)
                binding.etLocation.setAdapter(adapter)
                binding.etLocation.setOnClickListener { binding.etLocation.showDropDown() }
                binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) binding.etLocation.showDropDown()
                }
                binding.etSearchLocation.setAdapter(adapter)
                binding.etSearchLocation.setOnClickListener { binding.etSearchLocation.showDropDown() }
                binding.etSearchLocation.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) binding.etSearchLocation.showDropDown()
                }
            }
        }
    }

    private fun applyThresholdDropdownIcons() {
        binding.tilCreateLocation.setEndIconTintList(null)
        binding.tilSearchLocation.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilCreateLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
        binding.tilSearchLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
    }

    private fun applyThresholdTitleGradient() {
        binding.tvThresholdsTitle.post {
            val paint = binding.tvThresholdsTitle.paint
            val width = paint.measureText(binding.tvThresholdsTitle.text.toString())
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
            binding.tvThresholdsTitle.invalidate()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

}
