package com.example.inventoryapp.ui.categories
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.cache.CacheKeys
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.CategoryCreateDto
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.data.remote.model.CategoryListResponseDto
import com.example.inventoryapp.data.remote.model.CategoryUpdateDto
import com.example.inventoryapp.databinding.ActivityCategoriesBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.TopCenterActionHost
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import com.google.android.material.textfield.TextInputEditText
class CategoriesActivity : AppCompatActivity(), TopCenterActionHost {
    companion object {
        private const val OFFLINE_DELETE_MARKER = "offline_delete"
        @Volatile
        private var cacheNoticeShownInOfflineSession = false
    }

    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var adapter: CategoryListAdapter
    private lateinit var cacheStore: CacheStore
    private var items: List<CategoryResponseDto> = emptyList()
    private val gson = Gson()
    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var searchQuery: String = ""
    private var searchName: String? = null
    private var searchId: Int? = null
    private var createDialog: AlertDialog? = null
    private var searchDialog: AlertDialog? = null
    private var createCategoryInput: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        binding.btnRefreshCategories.setImageResource(R.drawable.glass_refresh)
        applyCategoriesTitleGradient()
        applyRefreshIconTint()
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        cacheStore = CacheStore.getInstance(this)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }
        adapter = CategoryListAdapter { category ->
            showEditDialog(category)
        }
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = adapter
        lifecycleScope.launch {
            NetworkModule.offlineState.collectLatest { offline ->
                if (!offline) {
                    cacheNoticeShownInOfflineSession = false
                }
            }
        }
        binding.btnRefreshCategories.setOnClickListener { loadCategories(withSnack = true) }
        binding.btnPrevPageCategories.setOnClickListener {
            if (searchId != null) return@setOnClickListener
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadCategories(withSnack = false)
            binding.rvCategories.scrollToPosition(0)
        }
        binding.btnNextPageCategories.setOnClickListener {
            if (searchId != null) return@setOnClickListener
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadCategories(withSnack = false)
            binding.rvCategories.scrollToPosition(0)
        }
        applyPagerButtonStyle(binding.btnPrevPageCategories, enabled = false)
        applyPagerButtonStyle(binding.btnNextPageCategories, enabled = false)
    }
    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadCategories()
        updateCategoriesListAdaptiveHeight()
    }

    override fun onTopCreateAction() {
        openCreateCategoryDialog()
    }

    override fun onTopFilterAction() {
        openSearchCategoryDialog()
    }
    private fun applySearch() {
        val raw = searchQuery.trim()
        if (raw.isBlank()) {
            clearSearch()
            return
        }
        val id = raw.toIntOrNull()
        if (id != null) {
            searchId = id
            searchName = null
            currentOffset = 0
            getById(id)
        } else {
            searchId = null
            searchName = raw
            currentOffset = 0
            loadCategories(name = raw, withSnack = false)
        }
    }
    private fun clearSearch() {
        searchQuery = ""
        searchId = null
        searchName = null
        currentOffset = 0
        loadCategories()
    }
    private fun getById(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getCategory(id)
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful && res.body() != null) {
                    items = markPendingDeletedCategories(listOf(res.body()!!))
                    cacheStore.put(CacheKeys.detail("categories", id), res.body()!!)
                    adapter.submit(items)
                    totalCount = 1
                    updatePageInfo(items.size, 0)
                } else {
                    if (res.code() == 404) {
                        showCategoryNotFoundDialog(query = id.toString(), byId = true)
                    } else {
                        UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code()))
                    }
                    adapter.submit(emptyList())
                    totalCount = 0
                    updatePageInfo(0, 0)
                }
            } catch (e: Exception) {
                val cached = cacheStore.get(CacheKeys.detail("categories", id), CategoryResponseDto::class.java)
                if (cached != null) {
                    items = listOf(cached)
                    adapter.submit(items)
                    totalCount = 1
                    updatePageInfo(items.size, 0)
                    // Only notify on API failure; cache-first rendering stays silent.
                } else {
                    if (e is IOException) {
                    } else {
                    }
                    adapter.submit(emptyList())
                    totalCount = 0
                    updatePageInfo(0, 0)
                }
            }
        }
    }
    private fun loadCategories(name: String? = searchName, withSnack: Boolean = false) {
        if (isLoading) return
        isLoading = true
        var postLoadingNotice: (() -> Unit)? = null
        val loading = if (withSnack) {
            CreateUiFeedback.showListLoading(
                this,
                message = "Cargando categorias",
                animationRes = R.raw.glass_loading_list,
                minCycles = 2
            )
        } else {
            null
        }
        lifecycleScope.launch {
            try {
                val includePending = name == null
                val pendingTotalCount = if (includePending) pendingCategoriesCount() else 0
                val cachedRemoteTotal = resolveCachedRemoteTotal(name)
                val cacheKey = CacheKeys.list(
                    "categories",
                    mapOf(
                        "name" to name,
                        "limit" to pageSize,
                        "offset" to currentOffset
                    )
                )
                val cached = cacheStore.get(cacheKey, CategoryListResponseDto::class.java)
                if (cached != null) {
                    val pending = pendingCategoriesForPage(
                        offset = currentOffset,
                        remoteTotal = cached.total,
                        includePending = includePending
                    )
                    items = markPendingDeletedCategories(cached.items + pending)
                    totalCount = cached.total + pendingTotalCount
                    adapter.submit(items)
                    updatePageInfo(items.size, pending.size)
                    isLoading = false
                }
                val res = NetworkModule.api.listCategories(name = name, limit = pageSize, offset = currentOffset)
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful && res.body() != null) {
                    cacheStore.put(cacheKey, res.body()!!)
                    val pending = pendingCategoriesForPage(
                        offset = currentOffset,
                        remoteTotal = res.body()!!.total,
                        includePending = includePending
                    )
                    items = markPendingDeletedCategories(res.body()!!.items + pending)
                    totalCount = res.body()!!.total + pendingTotalCount
                    adapter.submit(items)
                    updatePageInfo(items.size, pending.size)
                    if (name != null && currentOffset == 0 && res.body()!!.items.isEmpty()) {
                        showCategoryNotFoundDialog(query = name, byId = false)
                    }
                    if (withSnack) {
                        postLoadingNotice = {
                            CreateUiFeedback.showStatusPopup(
                                activity = this@CategoriesActivity,
                                title = "Categorias cargadas",
                                details = "Se han cargado correctamente.",
                                animationRes = R.raw.correct_create,
                                autoDismissMs = 2500L
                            )
                        }
                    }
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code()))
                    val cachedOnError = cacheStore.get(cacheKey, CategoryListResponseDto::class.java)
                    if (cachedOnError != null) {
                        val pending = pendingCategoriesForPage(
                            offset = currentOffset,
                            remoteTotal = cachedOnError.total,
                            includePending = includePending
                        )
                        items = markPendingDeletedCategories(cachedOnError.items + pending)
                        totalCount = cachedOnError.total + pendingTotalCount
                        adapter.submit(items)
                        updatePageInfo(items.size, pending.size)
                        if (withSnack && !cacheNoticeShownInOfflineSession) {
                            postLoadingNotice = { showCategoriesCacheNoticeOnce() }
                        } else {
                            showCategoriesCacheNoticeOnce()
                        }
                    } else {
                        val pending = pendingCategoriesForPage(
                            offset = currentOffset,
                            remoteTotal = cachedRemoteTotal,
                            includePending = includePending
                        )
                        items = markPendingDeletedCategories(pending)
                        adapter.submit(items)
                        totalCount = cachedRemoteTotal + pendingTotalCount
                        updatePageInfo(items.size, items.size)
                    }
                }
            } catch (e: Exception) {
                val includePending = name == null
                val pendingTotalCount = if (includePending) pendingCategoriesCount() else 0
                val cachedRemoteTotal = resolveCachedRemoteTotal(name)
                val cacheKey = CacheKeys.list(
                    "categories",
                    mapOf(
                        "name" to name,
                        "limit" to pageSize,
                        "offset" to currentOffset
                    )
                )
                val cachedOnError = cacheStore.get(cacheKey, CategoryListResponseDto::class.java)
                if (cachedOnError != null) {
                    val pending = pendingCategoriesForPage(
                        offset = currentOffset,
                        remoteTotal = cachedOnError.total,
                        includePending = includePending
                    )
                    items = markPendingDeletedCategories(cachedOnError.items + pending)
                    totalCount = cachedOnError.total + pendingTotalCount
                    adapter.submit(items)
                    updatePageInfo(items.size, pending.size)
                    if (withSnack && !cacheNoticeShownInOfflineSession) {
                        postLoadingNotice = { showCategoriesCacheNoticeOnce() }
                    } else {
                        showCategoriesCacheNoticeOnce()
                    }
                } else {
                    val pending = pendingCategoriesForPage(
                        offset = currentOffset,
                        remoteTotal = cachedRemoteTotal,
                        includePending = includePending
                    )
                    adapter.submit(markPendingDeletedCategories(pending))
                    totalCount = cachedRemoteTotal + pendingTotalCount
                    updatePageInfo(pending.size, pending.size)
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
            }
        }
    }

    private fun showCategoriesCacheNoticeOnce() {
        if (cacheNoticeShownInOfflineSession) return
        UiNotifier.showBlockingTimed(
            this,
            "Mostrando categorias en cache y pendientes offline",
            R.drawable.sync,
            timeoutMs = 3_200L
        )
        cacheNoticeShownInOfflineSession = true
    }
    private fun createCategory(nameRaw: String, onFinished: () -> Unit = {}) {
        val name = nameRaw.trim()
        if (name.isBlank()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se pudo crear categoria",
                details = "Nombre requerido"
            )
            onFinished()
            return
        }
        val loading = CreateUiFeedback.showLoading(this, "categoria")
        lifecycleScope.launch {
            var loadingHandled = false
            try {
                val res = NetworkModule.api.createCategory(CategoryCreateDto(name))
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful) {
                    val created = res.body()
                    val details = if (created != null) {
                        "ID: ${created.id}\nNombre: ${created.name}"
                    } else {
                        "Nombre: $name"
                    }
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showCreatedPopup(this@CategoriesActivity, "Categoria creada", details)
                    }
                    createCategoryInput = ""
                    currentOffset = 0
                    cacheStore.invalidatePrefix("categories")
                    loadCategories()
                } else {
                    val raw = runCatching { res.errorBody()?.string() }.getOrNull()
                    val details = formatCreateCategoryError(res.code(), raw)
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showErrorPopup(
                            activity = this@CategoriesActivity,
                            title = "No se pudo crear categoria",
                            details = details
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    OfflineQueue(this@CategoriesActivity).enqueue(PendingType.CATEGORY_CREATE, gson.toJson(CategoryCreateDto(name)))
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showCreatedPopup(
                            this@CategoriesActivity,
                            "Categoria creada (offline)",
                            "Nombre: $name (offline)",
                            accentColorRes = R.color.offline_text
                        )
                    }
                    currentOffset = 0
                    loadCategories()
                } else {
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showErrorPopup(
                            activity = this@CategoriesActivity,
                            title = "No se pudo crear categoria",
                            details = "Ha ocurrido un error inesperado al crear la categoria."
                        )
                    }
                }
            } finally {
                if (!loadingHandled) {
                    loading.dismiss()
                }
                onFinished()
            }
        }
    }

    private fun showEditDialog(category: CategoryResponseDto) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_category, null)
        val title = view.findViewById<android.widget.TextView>(R.id.tvEditCategoryTitle)
        val nameInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditCategoryName)
        val btnSave = view.findViewById<android.widget.Button>(R.id.btnEditCategorySave)
        val btnDelete = view.findViewById<android.widget.Button>(R.id.btnEditCategoryDelete)
        val btnClose = view.findViewById<android.widget.ImageButton>(R.id.btnEditCategoryClose)
        title.text = "Editar: ${category.name} (ID ${category.id})"
        nameInput.setText(category.name)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        btnSave.setOnClickListener {
            val newName = nameInput.text?.toString()?.trim().orEmpty()
            if (newName.isBlank()) {
                nameInput.error = "Nombre requerido"
                return@setOnClickListener
            }
            updateCategory(category.id, newName)
            dialog.dismiss()
        }
        btnDelete.setOnClickListener {
            CreateUiFeedback.showQuestionConfirmDialog(
                activity = this,
                title = "Eliminar categoría",
                message = "¿Seguro que quieres eliminar esta categoría?",
                confirmText = "Eliminar",
                cancelText = "Cancelar"
            ) {
                deleteCategory(category.id)
                dialog.dismiss()
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    private fun updateCategory(id: Int, name: String) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateCategory(id, CategoryUpdateDto(name = name))
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful) {
                    CreateUiFeedback.showCreatedPopup(
                        this@CategoriesActivity,
                        "CategorÃ­a actualizada",
                        "CategorÃ­a actualizada a: $name"
                    )
                    currentOffset = 0
                    cacheStore.invalidatePrefix("categories")
                    loadCategories()
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                } else {
                }
            }
        }
    }

    private fun showCategoryNotFoundDialog(query: String, byId: Boolean) {
        val details = if (byId) {
            "CategorÃ­a ID $query no encontrada"
        } else {
            "CategorÃ­a \"$query\" no encontrada"
        }
        CreateUiFeedback.showErrorPopup(
            activity = this,
            title = "CategorÃ­a no encontrada",
            details = details,
            animationRes = R.raw.notfound
        )
    }

    private fun formatCreateCategoryError(code: Int, rawError: String?): String {
        val raw = rawError?.trim().orEmpty()
        val normalized = raw.lowercase()
        val duplicateName =
            (normalized.contains("name") || normalized.contains("nombre") || normalized.contains("categor")) &&
                (normalized.contains("existe") || normalized.contains("exists") || normalized.contains("duplic"))

        if (duplicateName) {
            return buildString {
                append("Ya existe una categorÃ­a con ese nombre.")
                if (raw.isNotBlank()) append("\nDetalle: ${compactCategoryErrorDetail(raw)}")
                if (code > 0) append("\nHTTP $code")
            }
        }

        return buildString {
            append(
                when (code) {
                    400, 422 -> "Datos invÃ¡lidos para crear categorÃ­a."
                    403 -> "No tienes permisos para crear categorÃ­as."
                    409 -> "Conflicto al crear categorÃ­a."
                    500 -> "Error interno del servidor al crear categorÃ­a."
                    else -> "No se pudo crear la categorÃ­a."
                }
            )
            if (raw.isNotBlank()) append("\nDetalle: ${compactCategoryErrorDetail(raw)}")
            if (code > 0) append("\nHTTP $code")
        }
    }

    private fun compactCategoryErrorDetail(raw: String, maxLen: Int = 180): String {
        val singleLine = raw.replace("\\s+".toRegex(), " ").trim()
        return if (singleLine.length <= maxLen) singleLine else singleLine.take(maxLen) + "..."
    }
    private fun deleteCategory(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.deleteCategory(id)
                if (res.code() == 401) { return@launch }
                if (res.isSuccessful) {
                    currentOffset = 0
                    cacheStore.invalidatePrefix("categories")
                    loadCategories()
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    val payload = OfflineSyncer.CategoryDeletePayload(id)
                    OfflineQueue(this@CategoriesActivity).enqueue(PendingType.CATEGORY_DELETE, gson.toJson(payload))
                    CreateUiFeedback.showErrorPopup(
                        activity = this@CategoriesActivity,
                        title = "Sin conexion",
                        details = "No se pudo eliminar ahora. La solicitud se guardo en cola offline.",
                        animationRes = R.raw.error
                    )
                    currentOffset = 0
                    loadCategories()
                } else {
                }
            }
        }
    }
    private fun buildPendingCategories(): List<CategoryResponseDto> {
        val pending = OfflineQueue(this).getAll().filter { it.type == PendingType.CATEGORY_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, CategoryCreateDto::class.java) }.getOrNull()
            CategoryResponseDto(
                id = -1 - index,
                name = dto?.name ?: "CategorÃ­a offline",
                createdAt = "offline",
                updatedAt = null
            )
        }
    }
    private fun pendingCategoriesCount(): Int {
        return OfflineQueue(this).getAll().count { it.type == PendingType.CATEGORY_CREATE }
    }

    private fun pendingCategoryDeleteIds(): Set<Int> {
        return OfflineQueue(this).getAll()
            .asSequence()
            .filter { it.type == PendingType.CATEGORY_DELETE }
            .mapNotNull {
                runCatching {
                    gson.fromJson(it.payloadJson, OfflineSyncer.CategoryDeletePayload::class.java).categoryId
                }.getOrNull()
            }
            .toSet()
    }

    private fun markPendingDeletedCategories(rows: List<CategoryResponseDto>): List<CategoryResponseDto> {
        val pendingDeleteIds = pendingCategoryDeleteIds()
        if (pendingDeleteIds.isEmpty()) return rows
        return rows.map { category ->
            if (category.id > 0 && pendingDeleteIds.contains(category.id)) {
                val markedName = if (category.name.contains("(pendiente eliminar)", ignoreCase = true)) {
                    category.name
                } else {
                    "${category.name} (pendiente eliminar)"
                }
                category.copy(name = markedName, updatedAt = OFFLINE_DELETE_MARKER)
            } else {
                category
            }
        }
    }
    private fun pendingCategoriesForPage(
        offset: Int,
        remoteTotal: Int,
        includePending: Boolean
    ): List<CategoryResponseDto> {
        if (!includePending) return emptyList()
        val pendingAll = buildPendingCategories()
        if (pendingAll.isEmpty()) return emptyList()
        val startInPending = (offset - remoteTotal).coerceAtLeast(0)
        if (startInPending >= pendingAll.size) return emptyList()
        val endInPending = (offset + pageSize - remoteTotal)
            .coerceAtMost(pendingAll.size)
            .coerceAtLeast(startInPending)
        return pendingAll.subList(startInPending, endInPending)
    }
    private suspend fun resolveCachedRemoteTotal(name: String?): Int {
        val keyAtStart = CacheKeys.list(
            "categories",
            mapOf(
                "name" to name,
                "limit" to pageSize,
                "offset" to 0
            )
        )
        val cachedAtStart = cacheStore.get(keyAtStart, CategoryListResponseDto::class.java)
        return cachedAtStart?.total ?: 0
    }
    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        if (searchId != null) {
            binding.tvCategoriesPageNumber.text = "Pagina 1/1"
            binding.tvCategoriesPageInfo.text = "Mostrando ${items.size}/${items.size}"
            binding.btnPrevPageCategories.isEnabled = false
            binding.btnNextPageCategories.isEnabled = false
            applyPagerButtonStyle(binding.btnPrevPageCategories, enabled = false)
            applyPagerButtonStyle(binding.btnNextPageCategories, enabled = false)
            updateCategoriesListAdaptiveHeight()
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
        binding.tvCategoriesPageNumber.text = "Pagina $currentPage/$totalPages"
        binding.tvCategoriesPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shownOnline < totalItems
        binding.btnPrevPageCategories.isEnabled = prevEnabled
        binding.btnNextPageCategories.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevPageCategories, prevEnabled)
        applyPagerButtonStyle(binding.btnNextPageCategories, nextEnabled)
        updateCategoriesListAdaptiveHeight()
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
            binding.btnRefreshCategories.setColorFilter(blue)
        } else {
            binding.btnRefreshCategories.clearColorFilter()
        }
    }

    private fun openCreateCategoryDialog() {
        if (createDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_categories_create_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCreateCategoryDialogClose)
        val btnCreate = view.findViewById<Button>(R.id.btnDialogCreateCategory)
        val etName = view.findViewById<TextInputEditText>(R.id.etDialogCategoryName)

        etName.setText(createCategoryInput)

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            createCategoryInput = etName.text?.toString().orEmpty()
            btnCreate.isEnabled = false
            createCategory(createCategoryInput) { btnCreate.isEnabled = true }
            dialog.dismiss()
        }
        dialog.setOnDismissListener {
            createDialog = null
            hideKeyboard()
        }
        createDialog = dialog
        dialog.show()
    }

    private fun openSearchCategoryDialog() {
        if (searchDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_categories_search_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnSearchCategoryDialogClose)
        val btnSearch = view.findViewById<Button>(R.id.btnDialogSearchCategory)
        val btnClear = view.findViewById<Button>(R.id.btnDialogClearSearchCategory)
        val etQuery = view.findViewById<TextInputEditText>(R.id.etDialogCategoryQuery)

        etQuery.setText(searchQuery)

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnSearch.setOnClickListener {
            searchQuery = etQuery.text?.toString().orEmpty().trim()
            hideKeyboard()
            dialog.dismiss()
            applySearch()
        }
        btnClear.setOnClickListener {
            etQuery.setText("")
            clearSearch()
            hideKeyboard()
        }
        dialog.setOnDismissListener {
            searchDialog = null
            hideKeyboard()
        }
        searchDialog = dialog
        dialog.show()
    }

    private fun updateCategoriesListAdaptiveHeight() {
        binding.scrollCategories.post {
            val topSpacerLp = binding.viewCategoriesTopSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val bottomSpacerLp = binding.viewCategoriesBottomSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val cardLp = binding.cardCategoriesList.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val rvLp = binding.rvCategories.layoutParams as? LinearLayout.LayoutParams ?: return@post

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
                binding.rvCategories.isNestedScrollingEnabled = false
            } else {
                topSpacerLp.height = 0
                topSpacerLp.weight = 0f
                bottomSpacerLp.height = 0
                bottomSpacerLp.weight = 0f
                cardLp.height = 0
                cardLp.weight = 1f
                rvLp.height = 0
                rvLp.weight = 1f
                binding.rvCategories.isNestedScrollingEnabled = true
            }
            binding.viewCategoriesTopSpacer.layoutParams = topSpacerLp
            binding.viewCategoriesBottomSpacer.layoutParams = bottomSpacerLp
            binding.cardCategoriesList.layoutParams = cardLp
            binding.rvCategories.layoutParams = rvLp
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun applyCategoriesTitleGradient() {
        val title = binding.tvCategoriesTitle
        title.post {
            val paint = title.paint
            val width = paint.measureText(title.text.toString())
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
            title.invalidate()
        }
    }
}
