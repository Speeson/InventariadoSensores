package com.example.inventoryapp.ui.products
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.data.remote.model.ProductCreateDto
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.databinding.ActivityProductListBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.gson.Gson
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat

class ProductListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductListBinding
    private lateinit var session: SessionManager
    private lateinit var offlineQueue: OfflineQueue
    private val gson = Gson()
    private val products = mutableListOf<ProductResponseDto>()
    private lateinit var adapter: ProductListAdapter
    private var isLoading = false
    private var currentOffset = 0
    private val pageSize = 10
    private var currentSku: String? = null
    private var currentName: String? = null
    private var currentBarcode: String? = null
    private var categoryCache: Map<Int, String>? = null
    private var totalCount = 0
    private var sortAsc = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        session = SessionManager(this)
        offlineQueue = OfflineQueue(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnNewProduct.setOnClickListener {
            startActivity(Intent(this, ProductDetailActivity::class.java))
        }

        adapter = ProductListAdapter(
            onClick = { p ->
                val i = Intent(this, ProductDetailActivity::class.java)
                i.putExtra("product_id", p.id)
                startActivity(i)
            },
            onLabelClick = { p ->
                val i = Intent(this, LabelPreviewActivity::class.java)
                i.putExtra("product_id", p.id)
                i.putExtra("product_sku", p.sku)
                i.putExtra("product_barcode", p.barcode ?: "")
                startActivity(i)
            }
        )
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = adapter

        binding.btnPrevPage.setOnClickListener {
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadNextPage()
            binding.rvProducts.scrollToPosition(0)
        }
        binding.btnNextPage.setOnClickListener {
            val nextOffset = currentOffset + pageSize
            if (nextOffset >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadNextPage()
            binding.rvProducts.scrollToPosition(0)
        }
        binding.btnSortUp.setOnClickListener {
            sortAsc = true
            resetAndLoad(currentSku, currentName, currentBarcode)
        }
        binding.btnSortDown.setOnClickListener {
            sortAsc = false
            resetAndLoad(currentSku, currentName, currentBarcode)
        }

        binding.btnSearch.setOnClickListener { search() }

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
            resetAndLoad()
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                search()
                true
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        resetAndLoad()
    }

    private fun search() {
        val q = binding.etSearch.text.toString().trim()
        if (q.isBlank()) {
            resetAndLoad()
            return
        }

        val isBarcode = q.all { it.isDigit() } && q.length >= 8
        val looksLikeSku = !isBarcode && (q.contains("-") || (q.any { it.isLetter() } && q.any { it.isDigit() } && !q.contains(" ")))

        when {
            isBarcode -> resetAndLoad(barcode = q)
            looksLikeSku -> resetAndLoad(sku = q)
            else -> resetAndLoad(name = q)
        }
    }

    private fun resetAndLoad(
        sku: String? = null,
        name: String? = null,
        barcode: String? = null
    ) {
        currentSku = sku
        currentName = name
        currentBarcode = barcode
        currentOffset = 0
        isLoading = false
        products.clear()
        adapter.submit(emptyList())
        loadNextPage()
    }

    private fun loadNextPage() {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listProducts(
                    sku = currentSku,
                    name = currentName,
                    barcode = currentBarcode,
                    orderBy = "id",
                    orderDir = if (sortAsc) "asc" else "desc",
                    limit = pageSize,
                    offset = currentOffset
                )
                if (res.code() == 401) {
                    UiNotifier.show(this@ProductListActivity, ApiErrorFormatter.format(401))
                    val offlineItems = buildOfflineProducts()
                    if (offlineItems.isNotEmpty()) {
                        val categoryMap = fetchCategoryMap()
                        adapter.submit(offlineItems.map { ProductRowUi(it, categoryMap[it.categoryId]) })
                        UiNotifier.show(this@ProductListActivity, "Mostrando productos offline")
                    } else {
                        session.clearToken()
                        goToLogin()
                    }
                    isLoading = false
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    val pageItems = res.body()!!.items
                    if (pageItems.isEmpty() && currentOffset == 0) {
                        UiNotifier.show(this@ProductListActivity, "Sin resultados")
                    }
                    val categoryMap = categoryCache ?: fetchCategoryMap().also { categoryCache = it }
                    val rows = pageItems.map { ProductRowUi(it, categoryMap[it.categoryId]) }
                    products.clear()
                    products.addAll(pageItems)
                    adapter.submit(rows)
                    totalCount = res.body()!!.total
                    updatePageInfo(pageItems.size)

                } else {
                    val err = res.errorBody()?.string()
                    UiNotifier.show(this@ProductListActivity, ApiErrorFormatter.format(res.code(), err))
                    loadOfflineOnly()
                }
            } catch (e: Exception) {
                UiNotifier.show(this@ProductListActivity, "Error de red: ${e.message}")
                loadOfflineOnly()
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadOfflineOnly() {
        val items = if (sortAsc) {
            buildOfflineProducts().sortedBy { it.id }
        } else {
            buildOfflineProducts().sortedByDescending { it.id }
        }
        products.clear()
        products.addAll(items)
        totalCount = items.size
        currentOffset = 0
        lifecycleScope.launch {
            val categoryMap = fetchCategoryMap()
            adapter.submit(items.map { ProductRowUi(it, categoryMap[it.categoryId]) })
        }
        updatePageInfo(items.size)
        if (items.isNotEmpty()) {
            UiNotifier.show(this@ProductListActivity, "Mostrando productos offline")
        } else {
            UiNotifier.show(this@ProductListActivity, "Sin conexión y sin productos offline")
        }
    }

    private fun buildOfflineProducts(): List<ProductResponseDto> {
        val pending = offlineQueue.getAll().filter { it.type == com.example.inventoryapp.data.local.PendingType.PRODUCT_CREATE }
        return pending.mapIndexedNotNull { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, ProductCreateDto::class.java) }.getOrNull()
            if (dto == null) return@mapIndexedNotNull null
            ProductResponseDto(
                sku = dto.sku,
                name = "${dto.name} (offline)",
                barcode = dto.barcode,
                categoryId = dto.categoryId,
                active = dto.active ?: true,
                id = -1000 - index,
                createdAt = "offline",
                updatedAt = "offline"
            )
        }
    }

    private suspend fun fetchCategoryMap(): Map<Int, String> {
        return try {
            val res = NetworkModule.api.listCategories(limit = 100, offset = 0)
            if (res.isSuccessful && res.body() != null) {
                res.body()!!.items.associateBy({ it.id }, { it.name })
            } else emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun updatePageInfo(pageSizeLoaded: Int) {
        val shown = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        val label = if (totalCount > 0) {
            "Mostrando $shown / $totalCount"
        } else {
            "Mostrando 0 / 0"
        }
        binding.tvProductPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = (currentOffset + pageSize) < totalCount
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

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
