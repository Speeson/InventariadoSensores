package com.example.inventoryapp.ui.events
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.local.cache.CacheKeys
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventListResponseDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.data.remote.model.EventTypeDto
import com.example.inventoryapp.data.repository.remote.EventRepository
import com.example.inventoryapp.databinding.ActivityEventsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import com.example.inventoryapp.ui.common.GradientIconUtil
import android.view.View
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.inputmethod.InputMethodManager


class EventsActivity : AppCompatActivity() {
    companion object {
        @Volatile
        private var cacheNoticeShownInOfflineSession = false
    }

    private lateinit var binding: ActivityEventsBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack
    private lateinit var cacheStore: CacheStore

    private val gson = Gson()
    private val repo = EventRepository()
    private var items: List<EventRowUi> = emptyList()
    private var allItems: List<EventRowUi> = emptyList()
    private lateinit var adapter: EventAdapter
    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var filteredItems: List<EventRowUi> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false
    private var pendingSearchNotFoundDialog = false
    private var bulkProductNamesCache: Map<Int, String>? = null
    private var bulkProductNamesCacheAtMs: Long = 0L
    private val bulkProductNamesCacheTtlMs = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateEventAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivCreateEventSearch, R.drawable.search)
        applyEventsTitleGradient()
        binding.tilSearchType.post { applySearchDropdownIcon() }
        binding.tilCreateType.post { applyCreateDropdownIcon() }
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
snack = SendSnack(binding.root)
        session = SessionManager(this)
        cacheStore = CacheStore.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnCreateEvent.setOnClickListener { createEvent() }
        binding.btnRefresh.setOnClickListener {
            invalidateBulkProductNamesCache()
            loadEvents(withSnack = true)
        }
        binding.layoutCreateEventHeader.setOnClickListener { toggleCreateEventForm() }
        binding.layoutSearchEventHeader.setOnClickListener { toggleSearchForm() }
        binding.btnSearchEvents.setOnClickListener {
            hideKeyboard()
            applySearchFilters()
        }
        binding.btnClearSearch.setOnClickListener {
            hideKeyboard()
            clearSearchFilters()
        }
        binding.btnPrevPage.setOnClickListener {
            if (hasActiveFilters()) {
                if (filteredOffset <= 0) return@setOnClickListener
                filteredOffset = (filteredOffset - pageSize).coerceAtLeast(0)
                applyFilteredPage()
                binding.rvEvents.scrollToPosition(0)
                return@setOnClickListener
            }
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadEvents(withSnack = false)
            binding.rvEvents.scrollToPosition(0)
        }
        binding.btnNextPage.setOnClickListener {
            if (hasActiveFilters()) {
                val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
                if (shown >= filteredItems.size) return@setOnClickListener
                filteredOffset += pageSize
                applyFilteredPage()
                binding.rvEvents.scrollToPosition(0)
                return@setOnClickListener
            }
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadEvents(withSnack = false)
            binding.rvEvents.scrollToPosition(0)
        }

        adapter = EventAdapter(emptyList())
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter

        lifecycleScope.launch {
            NetworkModule.offlineState.collectLatest { offline ->
                if (!offline) {
                    cacheNoticeShownInOfflineSession = false
                }
            }
        }

        setupLocationDropdown()
        setupCreateDropdowns()
        setupSearchDropdowns()

        applyPagerButtonStyle(binding.btnPrevPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextPage, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadEvents(withSnack = false)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun createEvent() {
        val typeRawInput = binding.etEventType.text.toString().trim().uppercase()
        val productId = binding.etProductId.text.toString().trim().toIntOrNull()
        val delta = binding.etDelta.text.toString().trim().toIntOrNull()
        val location = normalizeLocationInput(binding.etLocation.text.toString().trim()).ifBlank { "default" }
        val source = binding.etSource.text.toString().trim().uppercase()

        val typeRaw = when (typeRawInput) {
            "IN" -> "SENSOR_IN"
            "OUT" -> "SENSOR_OUT"
            else -> typeRawInput
        }

        val eventType = when (typeRaw) {
            "SENSOR_IN" -> EventTypeDto.SENSOR_IN
            "SENSOR_OUT" -> EventTypeDto.SENSOR_OUT
            else -> { binding.etEventType.error = "Usa SENSOR_IN o SENSOR_OUT"; return }
        }

        if (productId == null) { binding.etProductId.error = "Product ID requerido"; return }
        if (delta == null || delta <= 0) { binding.etDelta.error = "Delta debe ser > 0"; return }
        if (source.isBlank()) { binding.etSource.error = "Fuente requerida"; return }

        val dto = EventCreateDto(
            eventType = eventType,
            productId = productId,
            delta = delta,
            source = source,
            location = location,
            idempotencyKey = UUID.randomUUID().toString()
        )

        binding.btnCreateEvent.isEnabled = false
        val loading = CreateUiFeedback.showLoading(this, "evento")

        lifecycleScope.launch {
            var loadingHandled = false
            try {
                val res = repo.createEvent(dto)
                if (res.isSuccess) {
                    val created = res.getOrNull()!!
                    val productName = getCachedProductNames()[created.productId]
                    val productLabel = productName?.let { "$it (${created.productId})" } ?: created.productId.toString()
                    val details = "ID: ${created.id}\nTipo: ${created.eventType}\nProducto: $productLabel\nCantidad: ${created.delta}\nUbicación: $location"
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showCreatedPopup(this@EventsActivity, "Evento creado", details)
                    }
                    binding.etDelta.setText("")
                    cacheStore.invalidatePrefix("events")
                    loadEvents(withSnack = false)
                } else {
                    val ex = res.exceptionOrNull()
                    if (ex is IOException) {
                        OfflineQueue(this@EventsActivity).enqueue(PendingType.EVENT_CREATE, gson.toJson(dto))
                        loadingHandled = true
                        loading.dismissThen {
                            CreateUiFeedback.showCreatedPopup(
                                this@EventsActivity,
                                "Evento creado (offline)",
                                "Tipo: ${dto.eventType}\nProducto: ${dto.productId}\nCantidad: ${dto.delta}\nUbicación: ${dto.location} (offline)",
                                accentColorRes = R.color.offline_text
                            )
                        }
                        loadEvents(withSnack = false)
                    } else {
                        if (isForbidden(ex)) {
                            UiNotifier.showBlocking(
                                this@EventsActivity,
                                "Permisos insuficientes",
                                "No tienes permisos para crear eventos.",
                                com.example.inventoryapp.R.drawable.ic_lock
                            )
                        } else {
                            snack.showError("Error: ${ex?.message ?: "sin detalle"}")
                        }
                    }
                }
            } catch (e: IOException) {
                OfflineQueue(this@EventsActivity).enqueue(PendingType.EVENT_CREATE, gson.toJson(dto))
                loadingHandled = true
                loading.dismissThen {
                    CreateUiFeedback.showCreatedPopup(
                        this@EventsActivity,
                        "Evento creado (offline)",
                        "Tipo: ${dto.eventType}\nProducto: ${dto.productId}\nCantidad: ${dto.delta}\nUbicación: ${dto.location} (offline)",
                        accentColorRes = R.color.offline_text
                    )
                }
                loadEvents(withSnack = false)
            } catch (e: Exception) {
                snack.showError("Error: ${e.message}")
            } finally {
                if (!loadingHandled) {
                    loading.dismiss()
                }
                binding.btnCreateEvent.isEnabled = true
            }
        }
    }

    private fun toggleCreateEventForm() {
        TransitionManager.beginDelayedTransition(binding.scrollEvents, AutoTransition().setDuration(180))
        val isVisible = binding.layoutCreateEventContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutCreateEventContent.visibility = View.GONE
            binding.layoutSearchEventContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutCreateEventContent.visibility = View.VISIBLE
            binding.layoutSearchEventContent.visibility = View.GONE
            setToggleActive(binding.layoutCreateEventHeader)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollEvents, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchEventContent.visibility == View.VISIBLE
        if (isVisible) {
            hideSearchForm()
        } else {
            binding.layoutSearchEventContent.visibility = View.VISIBLE
            binding.layoutCreateEventContent.visibility = View.GONE
            setToggleActive(binding.layoutSearchEventHeader)
        }
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true, showNotFoundDialog = true)
    }

    private fun applySearchFiltersInternal(
        allowReload: Boolean,
        showNotFoundDialog: Boolean = false
    ) {
        val typeRawInput = binding.etSearchType.text.toString().trim().uppercase()
        val productRaw = binding.etSearchProduct.text.toString().trim()
        val sourceRawInput = binding.etSearchSource.text.toString().trim().uppercase()

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > allItems.size)) {
            pendingFilterApply = true
            pendingSearchNotFoundDialog = showNotFoundDialog
            currentOffset = 0
            loadEvents(withSnack = false)
            return
        }

        val typeRaw = when (typeRawInput) {
            "IN" -> "SENSOR_IN"
            "OUT" -> "SENSOR_OUT"
            else -> typeRawInput
        }

        var filtered = allItems
        if (typeRaw.isNotBlank()) {
            filtered = filtered.filter { it.eventType.name == typeRaw }
        }
        if (productRaw.isNotBlank()) {
            val productId = productRaw.toIntOrNull()
            filtered = if (productId != null) {
                filtered.filter { it.productId == productId }
            } else {
                val needle = productRaw.lowercase()
                filtered.filter { it.productName?.lowercase()?.contains(needle) == true }
            }
        }
        if (sourceRawInput.isNotBlank()) {
            when (sourceRawInput) {
                "SCAN" -> {
                    filtered = filtered.filter { it.source.contains("scan", ignoreCase = true) }
                }
                "MANUAL" -> {
                    filtered = filtered.filter { !it.source.contains("scan", ignoreCase = true) }
                }
                else -> {
                    val needle = sourceRawInput.lowercase()
                    filtered = filtered.filter { it.source.lowercase().contains(needle) }
                }
            }
        }

        filteredItems = filtered
        filteredOffset = 0
        applyFilteredPage()
        if (showNotFoundDialog && hasActiveFilters() && filtered.isEmpty()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se encontraron eventos",
                details = buildEventSearchNotFoundDetails(typeRawInput, sourceRawInput, productRaw),
                animationRes = R.raw.notfound
            )
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun clearSearchFilters() {
        binding.etSearchType.setText("")
        binding.etSearchProduct.setText("")
        binding.etSearchSource.setText("")
        filteredItems = emptyList()
        filteredOffset = 0
        items = allItems
        adapter.submit(allItems)
        updatePageInfo(items.size, items.size)
    }

    private fun setupLocationDropdown() {
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
                    val adapter = ArrayAdapter(this@EventsActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etLocation.setAdapter(adapter)
                    binding.etLocation.setOnClickListener { binding.etLocation.showDropDown() }
                    binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etLocation.showDropDown()
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
                val adapter = ArrayAdapter(this@EventsActivity, android.R.layout.simple_list_item_1, allValues)
                binding.etLocation.setAdapter(adapter)
                binding.etLocation.setOnClickListener { binding.etLocation.showDropDown() }
                binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) binding.etLocation.showDropDown()
                }
            }
        }
    }


    private fun isForbidden(ex: Throwable?): Boolean {
        val msg = ex?.message ?: return false
        return msg.contains("HTTP 403")
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun loadEvents(withSnack: Boolean) {
        if (isLoading) return
        isLoading = true
        var postLoadingNotice: (() -> Unit)? = null
        val loading = if (withSnack) {
            CreateUiFeedback.showListLoading(
                this,
                message = "Cargando eventos",
                animationRes = R.raw.loading_list,
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
                val pendingTotalCount = pendingEventsCount()
                val cachedRemoteTotal = resolveCachedRemoteTotal(effectiveLimit)
                val cacheKey = CacheKeys.list(
                    "events",
                    mapOf(
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cached = cacheStore.get(cacheKey, EventListResponseDto::class.java)
                if (cached != null) {
                    val pendingItems = pendingRowsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cached.total,
                        filtersActive = filtersActive
                    )
                    val allProductIds = mutableSetOf<Int>()
                    cached.items.forEach { allProductIds.add(it.productId) }
                    pendingItems.forEach { allProductIds.add(it.productId) }
                    val productNames = fetchProductNames(allProductIds)
                    val cachedRows = cached.items.map { it.toRowUi(productNames) }
                    val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                    val ordered = (pendingWithNames + cachedRows).sortedByDescending { it.id }
                    totalCount = cached.total + pendingTotalCount
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pendingItems.size)
                }
                val res = repo.listEvents(limit = effectiveLimit, offset = effectiveOffset)
                if (res.isSuccess) {
                    val body = res.getOrNull()!!
                    cacheStore.put(cacheKey, body)
                    val remoteEvents = body.items
                    val pendingItems = pendingRowsForPage(
                        offset = effectiveOffset,
                        remoteTotal = body.total,
                        filtersActive = filtersActive
                    )
                    totalCount = body.total + pendingTotalCount
                    val allProductIds = mutableSetOf<Int>()
                    remoteEvents.forEach { allProductIds.add(it.productId) }
                    pendingItems.forEach { allProductIds.add(it.productId) }
                    val productNames = fetchProductNames(allProductIds)

                    val remoteItems = remoteEvents.map { it.toRowUi(productNames) }
                    val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                    val ordered = (pendingWithNames + remoteItems).sortedByDescending { it.id }
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pendingItems.size)
                    if (withSnack) {
                        postLoadingNotice = {
                            UiNotifier.showBlockingTimed(
                                this@EventsActivity,
                                "Eventos cargados",
                                R.drawable.loaded,
                                timeoutMs = 2_500L
                            )
                        }
                    }
                } else {
                    val ex = res.exceptionOrNull()
                    if (withSnack && ex !is IOException) {
                        snack.showError("Error: ${ex?.message ?: "sin detalle"}")
                    }
                    val cachedOnError = cacheStore.get(cacheKey, EventListResponseDto::class.java)
                    if (cachedOnError != null) {
                        val pendingItems = pendingRowsForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedOnError.total,
                            filtersActive = filtersActive
                        )
                        val allProductIds = mutableSetOf<Int>()
                        cachedOnError.items.forEach { allProductIds.add(it.productId) }
                        pendingItems.forEach { allProductIds.add(it.productId) }
                        val productNames = fetchProductNames(allProductIds)
                        val cachedRows = cachedOnError.items.map { it.toRowUi(productNames) }
                        val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                        val ordered = (pendingWithNames + cachedRows).sortedByDescending { it.id }
                        totalCount = cachedOnError.total + pendingTotalCount
                        setAllItemsAndApplyFilters(ordered)
                        updatePageInfo(ordered.size, pendingItems.size)
                        if (!cacheNoticeShownInOfflineSession) {
                            postLoadingNotice = cacheNoticePopupAction()
                        }
                    } else {
                        val pendingItems = pendingRowsForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedRemoteTotal,
                            filtersActive = filtersActive
                        )
                        val productNames = fetchProductNames(pendingItems.map { it.productId }.toSet())
                        val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                        val ordered = pendingWithNames.sortedByDescending { it.id }
                        totalCount = cachedRemoteTotal + pendingTotalCount
                        setAllItemsAndApplyFilters(ordered)
                        updatePageInfo(ordered.size, ordered.size)
                    }
                }
            } catch (e: Exception) {
                if (withSnack && e !is IOException) {
                    snack.showError("Error: ${e.message ?: "sin detalle"}")
                }
                val filtersActive = hasActiveFilters()
                val effectiveOffset = if (filtersActive) 0 else currentOffset
                val effectiveLimit = if (filtersActive) 100 else pageSize
                val pendingTotalCount = pendingEventsCount()
                val cachedRemoteTotal = resolveCachedRemoteTotal(effectiveLimit)
                val cacheKey = CacheKeys.list(
                    "events",
                    mapOf(
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cachedOnError = cacheStore.get(cacheKey, EventListResponseDto::class.java)
                if (cachedOnError != null) {
                    val pendingItems = pendingRowsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedOnError.total,
                        filtersActive = filtersActive
                    )
                    val allProductIds = mutableSetOf<Int>()
                    cachedOnError.items.forEach { allProductIds.add(it.productId) }
                    pendingItems.forEach { allProductIds.add(it.productId) }
                    val productNames = fetchProductNames(allProductIds)
                    val cachedRows = cachedOnError.items.map { it.toRowUi(productNames) }
                    val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                    val ordered = (pendingWithNames + cachedRows).sortedByDescending { it.id }
                    totalCount = cachedOnError.total + pendingTotalCount
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, pendingItems.size)
                    if (!cacheNoticeShownInOfflineSession) {
                        postLoadingNotice = cacheNoticePopupAction()
                    }
                } else {
                    val pendingItems = pendingRowsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedRemoteTotal,
                        filtersActive = filtersActive
                    )
                    val productNames = fetchProductNames(pendingItems.map { it.productId }.toSet())
                    val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                    val ordered = pendingWithNames.sortedByDescending { it.id }
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

    private fun setupSearchDropdowns() {
        val typeOptions = listOf("", "SENSOR_IN", "SENSOR_OUT")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, typeOptions)
        binding.etSearchType.setAdapter(typeAdapter)
        binding.etSearchType.setOnClickListener { binding.etSearchType.showDropDown() }
        binding.etSearchType.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSearchType.showDropDown()
        }

        val sourceOptions = listOf("", "SCAN", "MANUAL")
        val sourceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sourceOptions)
        binding.etSearchSource.setAdapter(sourceAdapter)
        binding.etSearchSource.setOnClickListener { binding.etSearchSource.showDropDown() }
        binding.etSearchSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSearchSource.showDropDown()
        }
    }

    private fun setupCreateDropdowns() {
        val typeOptions = listOf("", "SENSOR_IN", "SENSOR_OUT")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, typeOptions)
        binding.etEventType.setAdapter(typeAdapter)
        binding.etEventType.setOnItemClickListener { _, _, position, _ ->
            if (typeOptions[position].isBlank()) binding.etEventType.setText("", false)
        }
        binding.etEventType.setOnClickListener { binding.etEventType.showDropDown() }
        binding.etEventType.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etEventType.showDropDown()
        }

        val sourceOptions = listOf("", "SCAN", "MANUAL")
        val sourceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sourceOptions)
        binding.etSource.setAdapter(sourceAdapter)
        binding.etSource.setOnItemClickListener { _, _, position, _ ->
            if (sourceOptions[position].isBlank()) binding.etSource.setText("", false)
        }
        binding.etSource.setOnClickListener { binding.etSource.showDropDown() }
        binding.etSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSource.showDropDown()
        }
    }

    private fun applyCreateDropdownIcon() {
        binding.tilCreateType.setEndIconTintList(null)
        binding.tilCreateSource.setEndIconTintList(null)
        binding.tilCreateLocation.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        listOf(binding.tilCreateType, binding.tilCreateSource, binding.tilCreateLocation).forEach { til ->
            til.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
                GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
                iv.layoutParams = iv.layoutParams.apply {
                    width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                }
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
        }
    }

    private fun applySearchDropdownIcon() {
        binding.tilSearchType.setEndIconTintList(null)
        binding.tilSearchSource.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilSearchType.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
            iv.layoutParams = iv.layoutParams.apply {
                width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        binding.tilSearchSource.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
            iv.layoutParams = iv.layoutParams.apply {
                width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun pendingEventsCount(): Int {
        return OfflineQueue(this).getAll().count { it.type == PendingType.EVENT_CREATE }
    }

    private fun pendingRowsForPage(
        offset: Int,
        remoteTotal: Int,
        filtersActive: Boolean
    ): List<EventRowUi> {
        val pendingAll = buildPendingRows()
        if (pendingAll.isEmpty()) return emptyList()
        if (filtersActive) return pendingAll

        val startInPending = (offset - remoteTotal).coerceAtLeast(0)
        if (startInPending >= pendingAll.size) return emptyList()
        val endInPending = (offset + pageSize - remoteTotal)
            .coerceAtMost(pendingAll.size)
            .coerceAtLeast(startInPending)
        return pendingAll.subList(startInPending, endInPending)
    }

    private suspend fun resolveCachedRemoteTotal(limit: Int): Int {
        val keyAtStart = CacheKeys.list(
            "events",
            mapOf(
                "limit" to limit,
                "offset" to 0
            )
        )
        val cachedAtStart = cacheStore.get(keyAtStart, EventListResponseDto::class.java)
        if (cachedAtStart != null) return cachedAtStart.total

        if (limit != pageSize) {
            val keyDefault = CacheKeys.list(
                "events",
                mapOf(
                    "limit" to pageSize,
                    "offset" to 0
                )
            )
            val cachedDefault = cacheStore.get(keyDefault, EventListResponseDto::class.java)
            if (cachedDefault != null) return cachedDefault.total
        }
        return 0
    }

    private fun cacheNoticePopupAction(): () -> Unit {
        return {
            UiNotifier.showBlockingTimed(
                this,
                "Mostrando eventos en cache y pendientes offline",
                R.drawable.sync,
                timeoutMs = 3_200L
            )
            cacheNoticeShownInOfflineSession = true
        }
    }

    private fun buildPendingRows(): List<EventRowUi> {
        val queue = OfflineQueue(this)
        val pending = queue.getAll().filter { it.type == PendingType.EVENT_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, EventCreateDto::class.java) }.getOrNull()
            if (dto == null) {
                EventRowUi(
                    id = -1 - index,
                    eventType = EventTypeDto.SENSOR_IN,
                    productId = 0,
                    productName = null,
                    delta = 0,
                    source = "offline",
                    createdAt = "offline",
                    status = "PENDING",
                    isPending = true,
                    pendingMessage = "Guardado en modo offline, pendiente de sincronizacion"
                )
            } else {
                EventRowUi(
                    id = -1 - index,
                    eventType = dto.eventType,
                    productId = dto.productId,
                    productName = null,
                    delta = dto.delta,
                    source = dto.source,
                    createdAt = "offline",
                    status = "PENDING",
                    isPending = true,
                    pendingMessage = "Guardado en modo offline, pendiente de sincronizacion"
                )
            }
        }
    }

    private fun EventResponseDto.toRowUi(productNames: Map<Int, String>): EventRowUi {
        val status = eventStatus ?: if (processed) "PROCESSED" else "PENDING"
        return EventRowUi(
            id = id,
            eventType = eventType,
            productId = productId,
            productName = productNames[productId],
            delta = delta,
            source = source,
            createdAt = createdAt,
            status = status,
            isPending = false,
            pendingMessage = null
        )
    }

    private suspend fun fetchProductNames(ids: Set<Int>): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        val cachedMap = getCachedProductNames()
        val bulkNames = getOrFetchBulkProductNames()
        ids.forEach { id ->
            bulkNames[id]?.let { out[id] = it }
        }
        var networkFailed = false
        ids.forEach { id ->
            if (out.containsKey(id)) return@forEach
            if (!networkFailed) {
                try {
                    val res = NetworkModule.api.getProduct(id)
                    if (res.isSuccessful && res.body() != null) {
                        out[id] = res.body()!!.name
                        return@forEach
                    }
                } catch (_: Exception) {
                    networkFailed = true
                }
            }
            cachedMap[id]?.let { out[id] = it }
        }
        ids.forEach { id ->
            if (!out.containsKey(id)) cachedMap[id]?.let { out[id] = it }
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

    private suspend fun getCachedProductNames(): Map<Int, String> {
        val cached = cacheStore.get("products:names", com.example.inventoryapp.data.local.cache.ProductNameCache::class.java)
        return cached?.items?.associateBy({ it.id }, { it.name }) ?: emptyMap()
    }

    private fun setAllItemsAndApplyFilters(ordered: List<EventRowUi>) {
        allItems = ordered
        val hasFilters = hasActiveFilters()
        if (hasFilters || pendingFilterApply) {
            applySearchFiltersInternal(allowReload = false)
        } else {
            items = ordered
            adapter.submit(ordered)
        }
    }

    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        if (hasActiveFilters()) {
            val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
            binding.tvEventsPageInfo.text = "Mostrando $shown / ${filteredItems.size}"
            val prevEnabled = filteredOffset > 0
            val nextEnabled = shown < filteredItems.size
            binding.btnPrevPage.isEnabled = prevEnabled
            binding.btnNextPage.isEnabled = nextEnabled
            applyPagerButtonStyle(binding.btnPrevPage, prevEnabled)
            applyPagerButtonStyle(binding.btnNextPage, nextEnabled)
            return
        }
        val shownOnline = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        val label = if (totalCount > 0) {
            "Mostrando $shownOnline / $totalCount"
        } else {
            "Mostrando $pendingCount / $pendingCount"
        }
        binding.tvEventsPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shownOnline < totalCount
        binding.btnPrevPage.isEnabled = prevEnabled
        binding.btnNextPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextPage, nextEnabled)
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

    private fun hasActiveFilters(): Boolean {
        return binding.etSearchType.text?.isNotBlank() == true ||
            binding.etSearchProduct.text?.isNotBlank() == true ||
            binding.etSearchSource.text?.isNotBlank() == true
    }

    private fun buildEventSearchNotFoundDetails(
        typeRawInput: String,
        sourceRawInput: String,
        productRaw: String
    ): String {
        val parts = mutableListOf<String>()
        if (typeRawInput.isNotBlank()) {
            val typeLabel = when (typeRawInput) {
                "IN", "SENSOR_IN" -> "de tipo SENSOR_IN (entrada)"
                "OUT", "SENSOR_OUT" -> "de tipo SENSOR_OUT (salida)"
                else -> "de tipo $typeRawInput"
            }
            parts.add(typeLabel)
        }
        if (sourceRawInput.isNotBlank()) {
            parts.add("de la fuente $sourceRawInput")
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
            "No se encontraron eventos con los filtros actuales."
        } else {
            "No se encontraron eventos ${parts.joinToString(separator = " ")}."
        }
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        items = page
        adapter.submit(page)
        updatePageInfo(page.size, page.size)
    }

    private fun hideSearchForm() {
        binding.layoutSearchEventContent.visibility = View.GONE
        binding.layoutCreateEventContent.visibility = View.GONE
        setToggleActive(null)
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutCreateEventHeader) {
            binding.layoutCreateEventHeader.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.layoutSearchEventHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        } else if (active === binding.layoutSearchEventHeader) {
            binding.layoutCreateEventHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchEventHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutCreateEventHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchEventHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        }
    }

    private fun applyEventsTitleGradient() {
        val title = binding.tvEventsTitle
        title.post {
            val paint = title.paint
            val width = paint.measureText(title.text.toString())
            if (width <= 0f) return@post
            val c1 = androidx.core.content.ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_start)
            val c2 = androidx.core.content.ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid2)
            val c3 = androidx.core.content.ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid1)
            val c4 = androidx.core.content.ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_end)
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
            title.invalidate()
        }
    }

    private fun goToLogin() {
        if (!session.isTokenExpired()) return
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}


