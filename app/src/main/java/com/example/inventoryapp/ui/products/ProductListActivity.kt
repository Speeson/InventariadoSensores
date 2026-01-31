package com.example.inventoryapp.ui.products

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.databinding.ActivityProductListBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class ProductListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductListBinding
    private lateinit var session: SessionManager
    private var products: List<ProductResponseDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Toolbar + flecha
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnNewProduct.setOnClickListener {
            startActivity(Intent(this, ProductDetailActivity::class.java))
        }

        binding.lvProducts.setOnItemClickListener { _, _, position, _ ->
            val p = products[position]
            val i = Intent(this, ProductDetailActivity::class.java)
            i.putExtra("product_id", p.id)
            startActivity(i)
        }

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

        val maybeBarcode = q.all { it.isDigit() } && q.length >= 8

        loadProducts(
            sku = if (!maybeBarcode) q else null,
            name = q,
            barcode = if (maybeBarcode) q else null
        )
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
                    Toast.makeText(this@ProductListActivity, "Sesión caducada. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                    session.clearToken() // asegúrate de tener este método
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    products = res.body()!!.items

                    val itemsText = products.map { "(${it.id}) ${it.sku} - ${it.name}" }
                    binding.lvProducts.adapter = ArrayAdapter(
                        this@ProductListActivity,
                        android.R.layout.simple_list_item_1,
                        itemsText
                    )

                    if (products.isEmpty()) {
                        Toast.makeText(this@ProductListActivity, "Sin resultados", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val err = res.errorBody()?.string()
                    Toast.makeText(this@ProductListActivity, "Error ${res.code()}: ${err ?: "sin detalle"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProductListActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}