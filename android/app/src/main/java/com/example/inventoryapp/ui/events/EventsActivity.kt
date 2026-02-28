package com.example.inventoryapp.ui.events
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
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
import com.example.inventoryapp.ui.common.TopCenterActionHost
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import com.example.inventoryapp.ui.common.GradientIconUtil
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.LinearLayout


class EventsActivity : AppCompatActivity(), TopCenterActionHost {
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
    private var createDialog: AlertDialog? = null
    private var searchDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        binding.ivCreateEventAdd.setImageResource(R.drawable.glass_add)
        binding.ivCreateEventSearch.setImageResource(R.drawable.glass_search)
        applyHeaderIconTint()
        applyRefreshIconTint()
        applyEventsTitleGradient()
        applyDropdownPopupBackground()
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
        binding.layoutCreateEventHeader.setOnClickListener { openCreateEventDialog() }
        binding.layoutSearchEventHeader.setOnClickListener { openSearchEventDialog() }
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

    override fun onTopCreateAction() {
        openCreateEventDialog()
    }

    override fun onTopFilterAction() {
        openSearchEventDialog()
    }

    private fun createEvent() {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear eventos.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            return
        }

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
        if (delta == null || delta <= 0) { binding.etDelta.error = "Cantidad debe ser > 0"; return }
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
                    val status = awaitFinalEventStatus(
                        eventId = created.id,
                        initialStatus = created.eventStatus ?: if (created.processed) "PROCESSED" else "PENDING"
                    )
                    loadingHandled = true
                    if (status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true)) {
                        loading.dismissThen {
                            CreateUiFeedback.showErrorPopup(
                                activity = this@EventsActivity,
                                title = "Evento no procesado",
                                details = "El evento se envio pero quedo en estado fallido. Puedes revisarlo en Alertas o Actividades.",
                                animationRes = R.raw.error
                            )
                        }
                    } else {
                        val productName = getCachedProductNames()[created.productId]
                        val productLabel = productName?.let { "$it (${created.productId})" } ?: created.productId.toString()
                        val details = "ID: ${created.id}\nTipo: ${created.eventType}\nProducto: $productLabel\nCantidad: ${created.delta}\nUbicacion: $location"
                        loading.dismissThen {
                            CreateUiFeedback.showCreatedPopup(this@EventsActivity, "Evento creado", details)
                        }
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
                                "Tipo: ${dto.eventType}\nProducto: ${dto.productId}\nCantidad: ${dto.delta}\nUbicaciÃ³n: ${dto.location} (offline)",
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
                            val uiError = buildCreateEventErrorUi(ex)
                            loadingHandled = true
                            loading.dismissThen {
                                CreateUiFeedback.showErrorPopup(
                                    activity = this@EventsActivity,
                                    title = "No se pudo crear evento",
                                    details = uiError.details,
                                    animationRes = uiError.animationRes
                                )
                            }
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
                        "Tipo: ${dto.eventType}\nProducto: ${dto.productId}\nCantidad: ${dto.delta}\nUbicaciÃ³n: ${dto.location} (offline)",
                        accentColorRes = R.color.offline_text
                    )
                }
                loadEvents(withSnack = false)
            } catch (e: Exception) {
                val uiError = buildCreateEventErrorUi(e)
                loadingHandled = true
                loading.dismissThen {
                    CreateUiFeedback.showErrorPopup(
                        activity = this@EventsActivity,
                        title = "No se pudo crear evento",
                        details = uiError.details,
                        animationRes = uiError.animationRes
                    )
                }
            } finally {
                if (!loadingHandled) {
                    loading.dismiss()
                }
                binding.btnCreateEvent.isEnabled = true
            }
        }
    }

    private fun toggleCreateEventForm() {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear eventos.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            return
        }
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
        updateEventsListAdaptiveHeight()
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
                    val adapter = ArrayAdapter(this@EventsActivity, R.layout.item_liquid_dropdown, allValues)
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
                val adapter = ArrayAdapter(this@EventsActivity, R.layout.item_liquid_dropdown, allValues)
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

    private data class EventCreateErrorUi(
        val details: String,
        val animationRes: Int
    )

    private fun buildCreateEventErrorUi(ex: Throwable?): EventCreateErrorUi {
        val raw = ex?.message?.trim().orEmpty()
        val code = extractHttpCode(raw)
        val normalized = raw.lowercase()

        val productNotFound =
            (normalized.contains("producto") || normalized.contains("product")) &&
                (normalized.contains("not found") || normalized.contains("no encontrado") || normalized.contains("no existe"))

        val locationInvalid =
            (normalized.contains("ubic") || normalized.contains("location")) &&
                (normalized.contains("invalid") || normalized.contains("inval") || normalized.contains("not found") || normalized.contains("no existe"))

        if (productNotFound || code == 404) {
            val details = if (canShowTechnicalEventErrors()) {
                buildString {
                    append("No se puede crear el evento porque el producto no existe.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactEventErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "No se puede crear el evento porque el producto no existe."
            }
            return EventCreateErrorUi(details = details, animationRes = R.raw.notfound)
        }

        if (locationInvalid) {
            val details = if (canShowTechnicalEventErrors()) {
                buildString {
                    append("No se puede crear el evento porque la ubicacion no es valida.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactEventErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "No se puede crear el evento porque la ubicacion no es valida."
            }
            return EventCreateErrorUi(details = details, animationRes = R.raw.wrong)
        }

        val generic = if (canShowTechnicalEventErrors()) {
            buildString {
                append(
                    when (code) {
                        400, 422 -> "Datos invalidos para crear evento."
                        409 -> "Conflicto al crear evento."
                        500 -> "Error interno del servidor al crear evento."
                        else -> "No se pudo crear el evento."
                    }
                )
                if (raw.isNotBlank()) append("\nDetalle: ${compactEventErrorDetail(raw)}")
                if (code > 0) append("\nHTTP $code")
            }
        } else {
            when (code) {
                400, 422 -> "No se pudo crear el evento. Revisa los datos."
                409 -> "No se pudo crear el evento por conflicto de datos."
                500 -> "No se pudo crear el evento por un problema del servidor."
                else -> "No se pudo crear el evento. Intentalo de nuevo."
            }
        }
        return EventCreateErrorUi(details = generic, animationRes = R.raw.wrong)
    }

    private fun extractHttpCode(raw: String): Int {
        val match = Regex("HTTP\\s+(\\d{3})").find(raw) ?: return 0
        return match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun compactEventErrorDetail(raw: String, maxLen: Int = 180): String {
        val singleLine = raw.replace("\\s+".toRegex(), " ").trim()
        return if (singleLine.length <= maxLen) singleLine else singleLine.take(maxLen) + "..."
    }

    private fun canShowTechnicalEventErrors(): Boolean {
        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        return role.equals("ADMIN", ignoreCase = true) || role.equals("MANAGER", ignoreCase = true)
    }

    private fun isUserRole(): Boolean {
        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        return role.equals("USER", ignoreCase = true)
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
                            CreateUiFeedback.showStatusPopup(
                                activity = this@EventsActivity,
                                title = "Eventos cargados",
                                details = "Se han cargado correctamente.",
                                animationRes = R.raw.correct_create,
                                autoDismissMs = 2500L
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
        val typeAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, typeOptions)
        binding.etSearchType.setAdapter(typeAdapter)
        binding.etSearchType.setOnClickListener { binding.etSearchType.showDropDown() }
        binding.etSearchType.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSearchType.showDropDown()
        }

        val sourceOptions = listOf("", "SCAN", "MANUAL")
        val sourceAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, sourceOptions)
        binding.etSearchSource.setAdapter(sourceAdapter)
        binding.etSearchSource.setOnClickListener { binding.etSearchSource.showDropDown() }
        binding.etSearchSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSearchSource.showDropDown()
        }
    }

    private fun setupCreateDropdowns() {
        val typeOptions = listOf("", "SENSOR_IN", "SENSOR_OUT")
        val typeAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, typeOptions)
        binding.etEventType.setAdapter(typeAdapter)
        binding.etEventType.setOnItemClickListener { _, _, position, _ ->
            if (typeOptions[position].isBlank()) binding.etEventType.setText("", false)
        }
        binding.etEventType.setOnClickListener { binding.etEventType.showDropDown() }
        binding.etEventType.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etEventType.showDropDown()
        }

        val sourceOptions = listOf("", "SCAN", "MANUAL")
        val sourceAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, sourceOptions)
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
                iv.setImageResource(R.drawable.triangle_down_lg)
                iv.setColorFilter(ContextCompat.getColor(this, R.color.icon_grad_mid2))
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
            iv.setImageResource(R.drawable.triangle_down_lg)
            iv.setColorFilter(ContextCompat.getColor(this, R.color.icon_grad_mid2))
            iv.layoutParams = iv.layoutParams.apply {
                width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        binding.tilSearchSource.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            iv.setImageResource(R.drawable.triangle_down_lg)
            iv.setColorFilter(ContextCompat.getColor(this, R.color.icon_grad_mid2))
            iv.layoutParams = iv.layoutParams.apply {
                width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun openCreateEventFormFromTop() {
        openCreateEventDialog()
    }

    private fun openSearchEventFormFromTop() {
        openSearchEventDialog()
    }

    private fun openCreateEventDialog() {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear eventos.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            return
        }
        if (createDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_events_create_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCreateDialogClose)
        val btnCreate = view.findViewById<Button>(R.id.btnDialogCreateSubmit)
        val etType = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogCreateType)
        val etProductId = view.findViewById<TextInputEditText>(R.id.etDialogCreateProductId)
        val etDelta = view.findViewById<TextInputEditText>(R.id.etDialogCreateDelta)
        val etLocation = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogCreateLocation)
        val etSource = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogCreateSource)

        val createTypeOptions = listOf("", "SENSOR_IN", "SENSOR_OUT")
        val createSourceOptions = listOf("", "SCAN", "MANUAL")
        val createTypeAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, createTypeOptions)
        val createSourceAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, createSourceOptions)
        etType.setAdapter(createTypeAdapter)
        etSource.setAdapter(createSourceAdapter)
        etType.setOnClickListener { etType.showDropDown() }
        etType.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etType.showDropDown() }
        etSource.setOnClickListener { etSource.showDropDown() }
        etSource.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etSource.showDropDown() }

        val locationAdapter = binding.etLocation.adapter as? ArrayAdapter<String>
        if (locationAdapter != null) {
            etLocation.setAdapter(locationAdapter)
            etLocation.setOnClickListener { etLocation.showDropDown() }
            etLocation.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etLocation.showDropDown() }
        }

        applyDialogDropdownStyle(
            listOf(
                view.findViewById<TextInputLayout>(R.id.tilDialogCreateType),
                view.findViewById<TextInputLayout>(R.id.tilDialogCreateLocation),
                view.findViewById<TextInputLayout>(R.id.tilDialogCreateSource)
            ),
            listOf(etType, etLocation, etSource)
        )

        etType.setText(binding.etEventType.text?.toString().orEmpty(), false)
        etProductId.setText(binding.etProductId.text?.toString().orEmpty())
        etDelta.setText(binding.etDelta.text?.toString().orEmpty())
        etLocation.setText(binding.etLocation.text?.toString().orEmpty(), false)
        etSource.setText(binding.etSource.text?.toString().orEmpty(), false)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            binding.etEventType.setText(etType.text?.toString().orEmpty(), false)
            binding.etProductId.setText(etProductId.text?.toString().orEmpty())
            binding.etDelta.setText(etDelta.text?.toString().orEmpty())
            binding.etLocation.setText(etLocation.text?.toString().orEmpty(), false)
            binding.etSource.setText(etSource.text?.toString().orEmpty(), false)
            dialog.dismiss()
            createEvent()
        }

        dialog.setOnDismissListener {
            createDialog = null
            hideKeyboard()
        }

        createDialog = dialog
        dialog.show()
    }

    private fun openSearchEventDialog() {
        if (searchDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_events_search_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnSearchDialogClose)
        val btnSearch = view.findViewById<Button>(R.id.btnDialogSearchApply)
        val btnClear = view.findViewById<Button>(R.id.btnDialogSearchClear)
        val etType = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogSearchType)
        val etProduct = view.findViewById<TextInputEditText>(R.id.etDialogSearchProduct)
        val etSource = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogSearchSource)

        val searchTypeOptions = listOf("", "SENSOR_IN", "SENSOR_OUT")
        val searchSourceOptions = listOf("", "SCAN", "MANUAL")
        val searchTypeAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, searchTypeOptions)
        val searchSourceAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, searchSourceOptions)
        etType.setAdapter(searchTypeAdapter)
        etSource.setAdapter(searchSourceAdapter)
        etType.setOnClickListener { etType.showDropDown() }
        etType.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etType.showDropDown() }
        etSource.setOnClickListener { etSource.showDropDown() }
        etSource.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etSource.showDropDown() }

        applyDialogDropdownStyle(
            listOf(
                view.findViewById<TextInputLayout>(R.id.tilDialogSearchType),
                view.findViewById<TextInputLayout>(R.id.tilDialogSearchSource)
            ),
            listOf(etType, etSource)
        )

        etType.setText(binding.etSearchType.text?.toString().orEmpty(), false)
        etProduct.setText(binding.etSearchProduct.text?.toString().orEmpty())
        etSource.setText(binding.etSearchSource.text?.toString().orEmpty(), false)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnSearch.setOnClickListener {
            binding.etSearchType.setText(etType.text?.toString().orEmpty(), false)
            binding.etSearchProduct.setText(etProduct.text?.toString().orEmpty())
            binding.etSearchSource.setText(etSource.text?.toString().orEmpty(), false)
            hideKeyboard()
            dialog.dismiss()
            applySearchFilters()
        }
        btnClear.setOnClickListener {
            etType.setText("", false)
            etProduct.setText("")
            etSource.setText("", false)
            binding.etSearchType.setText("", false)
            binding.etSearchProduct.setText("")
            binding.etSearchSource.setText("", false)
            hideKeyboard()
            clearSearchFilters()
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
        val blue = ContextCompat.getColor(this, R.color.icon_grad_mid2)
        val popupDrawable = ContextCompat.getDrawable(this, R.drawable.bg_liquid_dropdown_popup)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        textInputLayouts.forEach { til ->
            til.setEndIconTintList(null)
            til.findViewById<android.widget.ImageView>(endIconId)?.let { icon ->
                icon.setImageResource(R.drawable.triangle_down_lg)
                icon.setColorFilter(blue)
            }
        }
        dropdowns.forEach { auto ->
            if (popupDrawable != null) {
                auto.setDropDownBackgroundDrawable(popupDrawable)
            }
        }
    }

    private fun applyHeaderIconTint() {
        val blue = ContextCompat.getColor(this, R.color.icon_grad_mid2)
        binding.ivCreateEventAdd.setColorFilter(blue)
        binding.ivCreateEventSearch.setColorFilter(blue)
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
            updateEventsListAdaptiveHeight()
        }
    }

    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        if (hasActiveFilters()) {
            val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
            binding.tvEventsPageInfo.text = "Mostrando $shown / ${filteredItems.size}"
            val currentPage = if (filteredItems.isEmpty()) 0 else (filteredOffset / pageSize) + 1
            binding.tvEventsPageNumber.text = "Pagina $currentPage"
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
        val currentPage = if (totalCount <= 0) 0 else (currentOffset / pageSize) + 1
        binding.tvEventsPageNumber.text = "Pagina $currentPage"
        val prevEnabled = currentOffset > 0
        val nextEnabled = shownOnline < totalCount
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

    private suspend fun awaitFinalEventStatus(
        eventId: Int?,
        initialStatus: String?
    ): String {
        val initial = (initialStatus ?: "PENDING").uppercase()
        if (eventId == null) return initial
        if (initial == "PROCESSED" || initial == "ERROR" || initial == "FAILED") return initial

        repeat(4) {
            delay(700)
            val res = runCatching { NetworkModule.api.listEvents(limit = 30, offset = 0) }.getOrNull()
            val status = res?.body()?.items?.firstOrNull { it.id == eventId }?.eventStatus?.uppercase()
            if (status == "PROCESSED" || status == "ERROR" || status == "FAILED") {
                return status
            }
        }
        return initial
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        items = page
        adapter.submit(page)
        updateEventsListAdaptiveHeight()
        updatePageInfo(page.size, page.size)
    }

    private fun updateEventsListAdaptiveHeight() {
        binding.scrollEvents.post {
            val cardLp = binding.cardEventsList.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val rvLp = binding.rvEvents.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val visibleCount = items.size

            if (visibleCount in 1 until pageSize) {
                cardLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                cardLp.weight = 0f
                rvLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                rvLp.weight = 0f
                binding.rvEvents.isNestedScrollingEnabled = false
            } else {
                cardLp.height = 0
                cardLp.weight = 1f
                rvLp.height = 0
                rvLp.weight = 1f
                binding.rvEvents.isNestedScrollingEnabled = true
            }
            binding.cardEventsList.layoutParams = cardLp
            binding.rvEvents.layoutParams = rvLp
        }
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

    private fun applyDropdownPopupBackground() {
        listOf(
            binding.etEventType,
            binding.etLocation,
            binding.etSource,
            binding.etSearchType,
            binding.etSearchSource
        ).forEach { auto ->
            ContextCompat.getDrawable(this, R.drawable.bg_liquid_dropdown_popup)?.let { drawable ->
                auto.setDropDownBackgroundDrawable(drawable)
            }
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


