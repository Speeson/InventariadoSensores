package com.example.inventoryapp.ui.products

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ProductCreateDto
import com.example.inventoryapp.data.remote.model.ProductUpdateDto
import com.example.inventoryapp.databinding.ActivityProductDetailBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var session: SessionManager
    private var productId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Toolbar + flecha
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        productId = intent.getIntExtra("product_id", -1).takeIf { it != -1 }

        if (productId == null) {
            supportActionBar?.title = "Nuevo producto"
            binding.btnDelete.isEnabled = false
        } else {
            supportActionBar?.title = "Editar producto #$productId"
            loadProduct(productId!!)
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadProduct(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getProduct(id)

                if (res.code() == 401) {
                    Toast.makeText(this@ProductDetailActivity, "Sesión caducada. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    val p = res.body()!!
                    binding.etSku.setText(p.sku)
                    binding.etName.setText(p.name)
                    binding.etBarcode.setText(p.barcode ?: "")
                    binding.etCategoryId.setText(p.categoryId.toString())

                    // en edición, no tocamos SKU
                    binding.etSku.isEnabled = false
                } else {
                    val err = res.errorBody()?.string()
                    Toast.makeText(this@ProductDetailActivity, "Error ${res.code()}: ${err ?: "sin detalle"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "Error red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun save() {
        val sku = binding.etSku.text.toString().trim()
        val name = binding.etName.text.toString().trim()
        val barcode = binding.etBarcode.text.toString().trim().ifBlank { null }
        val categoryId = binding.etCategoryId.text.toString().trim().toIntOrNull()

        if (productId == null && sku.isBlank()) { binding.etSku.error = "SKU requerido"; return }
        if (name.isBlank()) { binding.etName.error = "Nombre requerido"; return }
        if (categoryId == null) { binding.etCategoryId.error = "Category ID requerido"; return }

        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val res = if (productId == null) {
                    NetworkModule.api.createProduct(
                        ProductCreateDto(
                            sku = sku,
                            name = name,
                            barcode = barcode,
                            categoryId = categoryId,
                            active = true
                        )
                    )
                } else {
                    NetworkModule.api.updateProduct(
                        productId!!,
                        ProductUpdateDto(
                            name = name,
                            barcode = barcode,
                            categoryId = categoryId
                        )
                    )
                }

                if (res.code() == 401) {
                    Toast.makeText(this@ProductDetailActivity, "Sesión caducada. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    val p = res.body()!!
                    Toast.makeText(this@ProductDetailActivity, "Guardado ✅", Toast.LENGTH_SHORT).show()

                    // si era nuevo, pasa a modo edición
                    if (productId == null) {
                        productId = p.id
                        supportActionBar?.title = "Editar producto #${p.id}"
                        binding.btnDelete.isEnabled = true
                        binding.etSku.isEnabled = false
                    }
                } else {
                    val err = res.errorBody()?.string()
                    Toast.makeText(this@ProductDetailActivity, "Error ${res.code()}: ${err ?: "sin detalle"}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "Error red: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }

    private fun confirmDelete() {
        if (productId == null) return

        AlertDialog.Builder(this)
            .setTitle("Eliminar producto")
            .setMessage("¿Seguro que quieres eliminar este producto?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ -> deleteProduct(productId!!) }
            .show()
    }

    private fun deleteProduct(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.deleteProduct(id)

                if (res.code() == 401) {
                    Toast.makeText(this@ProductDetailActivity, "Sesión caducada. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (res.isSuccessful) {
                    Toast.makeText(this@ProductDetailActivity, "Eliminado ✅", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val err = res.errorBody()?.string()
                    Toast.makeText(this@ProductDetailActivity, "Error ${res.code()}: ${err ?: "sin detalle"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProductDetailActivity, "Error red: ${e.message}", Toast.LENGTH_LONG).show()
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