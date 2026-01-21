package com.example.inventoryapp.ui.products

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.data.repository.fake.FakeProductRepository
import com.example.inventoryapp.databinding.ActivityProductDetailBinding
import com.example.inventoryapp.ui.scan.ScanActivity

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private val repo = FakeProductRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sku = intent.getStringExtra("sku").orEmpty()
        val product = repo.findBySkuOrName(sku)

        binding.tvTitle.text = product?.name ?: "Producto no encontrado"
        binding.tvInfo.text = if (product != null) {
            "SKU: ${product.sku}\nCategoría: ${product.category}\nStock: ${product.stock}"
        } else {
            "No se encontró info para: $sku"
        }

        binding.btnRegisterMovement.setOnClickListener {
            // Abrimos tu flujo
            startActivity(Intent(this, ScanActivity::class.java))
        }
    }
}
