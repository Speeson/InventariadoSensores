package com.example.inventoryapp.ui.rotation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementTypeDto
import com.example.inventoryapp.databinding.ActivityRotationBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import kotlinx.coroutines.launch

class RotationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRotationBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack
    private lateinit var adapter: RotationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRotationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        snack = SendSnack(binding.root)
        session = SessionManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = RotationAdapter(emptyList())
        binding.rvRotation.layoutManager = LinearLayoutManager(this)
        binding.rvRotation.adapter = adapter

        binding.btnRefreshRotation.setOnClickListener { loadRotation(withSnack = true) }
    }

    override fun onResume() {
        super.onResume()
        loadRotation(withSnack = false)
    }

    private fun loadRotation(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando rotacion...")

        lifecycleScope.launch {
            try {
                val movRes = NetworkModule.api.listMovements(limit = 200, offset = 0)
                val prodRes = NetworkModule.api.listProducts(limit = 200, offset = 0)
                val stockRes = NetworkModule.api.listStocks(limit = 200, offset = 0)

                if (movRes.code() == 401 || prodRes.code() == 401 || stockRes.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (!movRes.isSuccessful || movRes.body() == null) {
                    snack.showError("Error movimientos: HTTP ${movRes.code()}")
                    return@launch
                }
                if (!prodRes.isSuccessful || prodRes.body() == null) {
                    snack.showError("Error productos: HTTP ${prodRes.code()}")
                    return@launch
                }
                if (!stockRes.isSuccessful || stockRes.body() == null) {
                    snack.showError("Error stock: HTTP ${stockRes.code()}")
                    return@launch
                }

                val products = prodRes.body()!!.items.associateBy { it.id }
                val movements = movRes.body()!!.items
                val stocks = stockRes.body()!!.items

                val inMap = mutableMapOf<Int, Int>()
                val outMap = mutableMapOf<Int, Int>()
                for (m in movements) {
                    val current = if (m.movementType == MovementTypeDto.IN) {
                        inMap.getOrDefault(m.productId, 0) + m.quantity
                    } else if (m.movementType == MovementTypeDto.OUT) {
                        outMap.getOrDefault(m.productId, 0) + m.quantity
                    } else {
                        continue
                    }
                    if (m.movementType == MovementTypeDto.IN) {
                        inMap[m.productId] = current
                    } else if (m.movementType == MovementTypeDto.OUT) {
                        outMap[m.productId] = current
                    }
                }

                val stockMap = mutableMapOf<Int, Int>()
                for (s in stocks) {
                    stockMap[s.productId] = stockMap.getOrDefault(s.productId, 0) + s.quantity
                }

                val productIds = (inMap.keys + outMap.keys + stockMap.keys).toSet()
                val rows = productIds.map { id ->
                    val p = products[id]
                    RotationRow(
                        productId = id,
                        sku = p?.sku ?: "SKU-$id",
                        name = p?.name ?: "Producto $id",
                        totalIn = inMap.getOrDefault(id, 0),
                        totalOut = outMap.getOrDefault(id, 0),
                        stock = stockMap.getOrDefault(id, 0)
                    )
                }.sortedByDescending { it.totalOut }

                adapter.submit(rows)
                if (withSnack) snack.showSuccess("OK: Rotacion cargada")

            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
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
