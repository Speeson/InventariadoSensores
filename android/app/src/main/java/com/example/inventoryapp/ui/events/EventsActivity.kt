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
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventResponseDto
import com.example.inventoryapp.data.remote.model.EventTypeDto
import com.example.inventoryapp.data.repository.remote.EventRepository
import com.example.inventoryapp.databinding.ActivityEventsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import com.example.inventoryapp.ui.common.GradientIconUtil
import android.view.View
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.inputmethod.InputMethodManager


class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateEventAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivCreateEventSearch, R.drawable.search)
        applyEventsTitleGradient()
        binding.tilSearchType.post { applySearchDropdownIcon() }
        binding.tilCreateType.post { applyCreateDropdownIcon() }
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
snack = SendSnack(binding.root)
        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnCreateEvent.setOnClickListener { createEvent() }
        binding.btnRefresh.setOnClickListener { loadEvents(withSnack = true) }
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

        adapter = EventAdapter(emptyList()) { row ->
            snack.showError(row.pendingMessage ?: "Guardado en modo offline")
        }
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter

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
        snack.showSending("Enviando evento...")

        lifecycleScope.launch {
            try {
                val res = repo.createEvent(dto)
                if (res.isSuccess) {
                    snack.showSuccess("OK: Evento creado")
                    binding.etDelta.setText("")
                    loadEvents(withSnack = false)
                } else {
                    val ex = res.exceptionOrNull()
                    if (ex is IOException) {
                        OfflineQueue(this@EventsActivity).enqueue(PendingType.EVENT_CREATE, gson.toJson(dto))
                        snack.showQueuedOffline("Sin conexión. Evento guardado offline")
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
                snack.showQueuedOffline("Sin conexión. Evento guardado offline")
                loadEvents(withSnack = false)
            } catch (e: Exception) {
                snack.showError("Error: ${e.message}")
            } finally {
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
        applySearchFiltersInternal(allowReload = true)
    }

    private fun applySearchFiltersInternal(allowReload: Boolean) {
        val typeRawInput = binding.etSearchType.text.toString().trim().uppercase()
        val productRaw = binding.etSearchProduct.text.toString().trim()
        val sourceRawInput = binding.etSearchSource.text.toString().trim().uppercase()

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > allItems.size)) {
            pendingFilterApply = true
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
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
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
        if (withSnack) snack.showSending("Cargando eventos...")

        lifecycleScope.launch {
            try {
                val filtersActive = hasActiveFilters()
                val effectiveLimit = if (filtersActive) 100 else pageSize
                val effectiveOffset = if (filtersActive) 0 else currentOffset
                if (filtersActive) currentOffset = 0
                val res = repo.listEvents(limit = effectiveLimit, offset = effectiveOffset)
                if (res.isSuccess) {
                    val body = res.getOrNull()!!
                    val remoteEvents = body.items
                    totalCount = body.total
                    val pendingItems = buildPendingRows()
                    val allProductIds = mutableSetOf<Int>()
                    remoteEvents.forEach { allProductIds.add(it.productId) }
                    pendingItems.forEach { allProductIds.add(it.productId) }
                    val productNames = fetchProductNames(allProductIds)

                    val remoteItems = remoteEvents.map { it.toRowUi(productNames) }
                    val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                    val ordered = (pendingWithNames + remoteItems).sortedByDescending { it.id }
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(remoteEvents.size, pendingItems.size)
                    if (withSnack) snack.showSuccess("OK: Eventos cargados")
                } else {
                    val ex = res.exceptionOrNull()
                    if (withSnack) {
                        if (ex is IOException) {
                            snack.showError("Sin conexión a Internet")
                        } else {
                            snack.showError("Error: ${ex?.message ?: "sin detalle"}")
                        }
                    }
                    val pendingItems = buildPendingRows()
                    val productNames = fetchProductNames(pendingItems.map { it.productId }.toSet())
                    val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                    val ordered = pendingWithNames.sortedByDescending { it.id }
                    totalCount = pendingItems.size
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, ordered.size)
                }
            } catch (e: Exception) {
                if (withSnack) {
                    if (e is IOException) {
                        snack.showError("Sin conexión a Internet")
                    } else {
                        snack.showError("Error de red: ${e.message}")
                    }
                }
                val pendingItems = buildPendingRows()
                val productNames = fetchProductNames(pendingItems.map { it.productId }.toSet())
                val pendingWithNames = pendingItems.map { it.copy(productName = productNames[it.productId]) }
                val ordered = pendingWithNames.sortedByDescending { it.id }
                totalCount = pendingItems.size
                setAllItemsAndApplyFilters(ordered)
                updatePageInfo(ordered.size, ordered.size)
            }
            isLoading = false
            if (pendingFilterApply) {
                pendingFilterApply = false
                applySearchFiltersInternal(allowReload = false)
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
        ids.forEach { id ->
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
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
