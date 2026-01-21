package com.example.inventoryapp.ui.products

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.repository.fake.FakeProductRepository
import com.example.inventoryapp.databinding.ActivityProductListBinding

class ProductListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductListBinding
    private val repo = FakeProductRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val products = repo.getProducts()

        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = ProductAdapter(products) { p ->
            val i = Intent(this, ProductDetailActivity::class.java)
            i.putExtra("sku", p.sku)
            startActivity(i)
        }
    }
}
