package com.example.inventoryapp.ui.categories

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.CategoryCreateDto
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.data.remote.model.CategoryUpdateDto
import com.example.inventoryapp.databinding.ActivityCategoriesBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.auth.LoginActivity
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class CategoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: CategoryListAdapter
    private var items: List<CategoryResponseDto> = emptyList()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = CategoryListAdapter { category ->
            showEditDialog(category)
        }
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = adapter

        binding.btnCreate.setOnClickListener { createCategory() }
        binding.btnSearch.setOnClickListener { search() }
        binding.btnClear.setOnClickListener {
            binding.etSearchId.setText("")
            binding.etSearchName.setText("")
            loadCategories()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCategories()
    }

    private fun search() {
        val id = binding.etSearchId.text.toString().trim().toIntOrNull()
        val name = binding.etSearchName.text.toString().trim().ifBlank { null }
        if (id != null) {
            getById(id)
        } else {
            loadCategories(name = name)
        }
    }

    private fun getById(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getCategory(id)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful && res.body() != null) {
                    val pending = buildPendingCategories()
                    items = pending + listOf(res.body()!!)
                    adapter.submit(items)
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code()))
                    adapter.submit(emptyList())
                }
            } catch (e: Exception) {
                val pending = buildPendingCategories()
                adapter.submit(pending)
                if (e is IOException) {
                    UiNotifier.show(this@CategoriesActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@CategoriesActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun loadCategories(name: String? = null) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listCategories(name = name, limit = 100, offset = 0)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful && res.body() != null) {
                    val pending = buildPendingCategories()
                    items = pending + res.body()!!.items
                    adapter.submit(items)
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code()))
                }
            } catch (e: Exception) {
                val pending = buildPendingCategories()
                adapter.submit(pending)
                if (e is IOException) {
                    UiNotifier.show(this@CategoriesActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@CategoriesActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun createCategory() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) { binding.etName.error = "Nombre requerido"; return }

        binding.btnCreate.isEnabled = false
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createCategory(CategoryCreateDto(name))
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    binding.etName.setText("")
                    loadCategories()
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    OfflineQueue(this@CategoriesActivity).enqueue(PendingType.CATEGORY_CREATE, gson.toJson(CategoryCreateDto(name)))
                    UiNotifier.show(this@CategoriesActivity, "Sin conexión. Categoria guardada offline")
                    loadCategories()
                } else {
                    UiNotifier.show(this@CategoriesActivity, "Error de red: ${e.message}")
                }
            } finally {
                binding.btnCreate.isEnabled = true
            }
        }
    }

    private fun showEditDialog(category: CategoryResponseDto) {
        val input = android.widget.EditText(this).apply {
            setText(category.name)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar categoría #${category.id}")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Eliminar") { _, _ ->
                deleteCategory(category.id)
            }
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) {
                    UiNotifier.show(this, "Nombre requerido")
                } else {
                    updateCategory(category.id, newName)
                }
            }
            .show()
    }

    private fun updateCategory(id: Int, name: String) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateCategory(id, CategoryUpdateDto(name = name))
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    loadCategories()
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    UiNotifier.show(this@CategoriesActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@CategoriesActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun deleteCategory(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.deleteCategory(id)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    loadCategories()
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    UiNotifier.show(this@CategoriesActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@CategoriesActivity, "Error de red: ${e.message}")
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
                name = dto?.name ?: "Categoria offline",
                createdAt = "offline",
                updatedAt = null
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
