package com.example.inventoryapp.ui.products
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
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

class ProductListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductListBinding
    private lateinit var session: SessionManager
    private lateinit var offlineQueue: OfflineQueue
    private val gson = Gson()
    private var products: List<ProductResponseDto> = emptyList()
    private lateinit var adapter: ProductListAdapter

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

        binding.btnSearch.setOnClickListener { search() }

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
            loadProducts()
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
        loadProducts()
    }

    private fun search() {
        val q = binding.etSearch.text.toString().trim()
        if (q.isBlank()) {
            loadProducts()
            return
        }

        val isBarcode = q.all { it.isDigit() } && q.length >= 8
        val looksLikeSku = !isBarcode && (q.contains("-") || (q.any { it.isLetter() } && q.any { it.isDigit() } && !q.contains(" ")))

        when {
            isBarcode -> loadProducts(barcode = q)
            looksLikeSku -> loadProducts(sku = q)
            else -> loadProducts(name = q)
        }
    }

    private fun loadProducts(
        sku: String? = null,
        name: String? = null,
        barcode: String? = null
    ) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listProducts(
                    sku = sku,
                    name = name,
                    barcode = barcode,
                    limit = 50,
                    offset = 0
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
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    products = res.body()!!.items
                    val categoryMap = fetchCategoryMap()
                    val rows = products.map { ProductRowUi(it, categoryMap[it.categoryId]) }
                    adapter.submit(rows)

                    if (products.isEmpty()) {
                        UiNotifier.show(this@ProductListActivity, "Sin resultados")
                    }
                } else {
                    val err = res.errorBody()?.string()
                    UiNotifier.show(this@ProductListActivity, ApiErrorFormatter.format(res.code(), err))
                    loadOfflineOnly()
                }
            } catch (e: Exception) {
                UiNotifier.show(this@ProductListActivity, "Error de red: ${e.message}")
                loadOfflineOnly()
            }
        }
    }

    private fun loadOfflineOnly() {
        val items = buildOfflineProducts()
        products = items
        lifecycleScope.launch {
            val categoryMap = fetchCategoryMap()
            adapter.submit(items.map { ProductRowUi(it, categoryMap[it.categoryId]) })
        }
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

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
