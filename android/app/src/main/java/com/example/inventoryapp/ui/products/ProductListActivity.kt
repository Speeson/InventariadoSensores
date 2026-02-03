package com.example.inventoryapp.ui.products

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ProductCreateDto
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.databinding.ActivityProductListBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.gson.Gson
import kotlinx.coroutines.launch

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

        session = SessionManager(this)
        offlineQueue = OfflineQueue(this)

        // Toolbar + flecha
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnNewProduct.setOnClickListener {
            startActivity(Intent(this, ProductDetailActivity::class.java))
        }

        adapter = ProductListAdapter { p ->
            val i = Intent(this, ProductDetailActivity::class.java)
            i.putExtra("product_id", p.id)
            startActivity(i)
        }
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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
                        adapter.submit(offlineItems)
                        UiNotifier.show(this@ProductListActivity, "Mostrando productos offline")
                    } else {
                        session.clearToken() // asegurate de tener este metodo
                        goToLogin()
                    }
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    products = res.body()!!.items
                    adapter.submit(products)

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
        adapter.submit(items)
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

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
