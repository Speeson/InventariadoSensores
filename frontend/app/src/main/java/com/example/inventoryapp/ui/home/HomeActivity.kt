package com.example.inventoryapp.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.databinding.ActivityHomeBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.scan.ScanActivity
import com.example.inventoryapp.ui.products.ProductListActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Si no hay token, fuera
        if (session.getToken().isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        binding.btnProducts.setOnClickListener {
            startActivity(Intent(this, ProductListActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            session.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
