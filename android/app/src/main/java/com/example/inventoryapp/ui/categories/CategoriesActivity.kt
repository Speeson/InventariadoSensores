package com.example.inventoryapp.ui.categories

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.CategoryCreateDto
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.data.remote.model.CategoryUpdateDto
import com.example.inventoryapp.databinding.ActivityCategoriesBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

class CategoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: CategoryListAdapter
    private var items: List<CategoryResponseDto> = emptyList()
    private val gson = Gson()
    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var searchName: String? = null
    private var searchId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateCategoryAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivCreateCategorySearch, R.drawable.search)
        applyCategoriesTitleGradient()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
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

        binding.layoutCreateCategoryHeader.setOnClickListener { toggleCreateForm() }
        binding.layoutSearchCategoryHeader.setOnClickListener { toggleSearchForm() }
        binding.btnCreateCategory.setOnClickListener { createCategory() }
        binding.btnSearchCategory.setOnClickListener {
            hideKeyboard()
            applySearch()
        }
        binding.btnClearCategory.setOnClickListener {
            hideKeyboard()
            clearSearch()
        }
        binding.btnRefreshCategories.setOnClickListener { loadCategories(withSnack = true) }
        binding.btnPrevPageCategories.setOnClickListener {
            if (searchId != null) return@setOnClickListener
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadCategories(withSnack = false)
            binding.rvCategories.scrollToPosition(0)
        }
        binding.btnNextPageCategories.setOnClickListener {
            if (searchId != null) return@setOnClickListener
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadCategories(withSnack = false)
            binding.rvCategories.scrollToPosition(0)
        }

        applyPagerButtonStyle(binding.btnPrevPageCategories, enabled = false)
        applyPagerButtonStyle(binding.btnNextPageCategories, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadCategories()
    }

    private fun applySearch() {
        val raw = binding.etSearchQuery.text.toString().trim()
        if (raw.isBlank()) {
            clearSearch()
            return
        }
        val id = raw.toIntOrNull()
        if (id != null) {
            searchId = id
            searchName = null
            currentOffset = 0
            getById(id)
        } else {
            searchId = null
            searchName = raw
            currentOffset = 0
            loadCategories(name = raw, withSnack = false)
        }
    }

    private fun clearSearch() {
        searchId = null
        searchName = null
        binding.etSearchQuery.setText("")
        currentOffset = 0
        loadCategories()
    }

    private fun getById(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getCategory(id)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful && res.body() != null) {
                    items = listOf(res.body()!!)
                    adapter.submit(items)
                    totalCount = 1
                    updatePageInfo(items.size, 0)
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code()))
                    adapter.submit(emptyList())
                    totalCount = 0
                    updatePageInfo(0, 0)
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    UiNotifier.show(this@CategoriesActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@CategoriesActivity, "Error de red: ${e.message}")
                }
                adapter.submit(emptyList())
                totalCount = 0
                updatePageInfo(0, 0)
            }
        }
    }

    private fun loadCategories(name: String? = searchName, withSnack: Boolean = false) {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listCategories(name = name, limit = pageSize, offset = currentOffset)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful && res.body() != null) {
                    val pending = if (name == null && currentOffset == 0) buildPendingCategories() else emptyList()
                    items = pending + res.body()!!.items
                    totalCount = res.body()!!.total
                    adapter.submit(items)
                    updatePageInfo(res.body()!!.items.size, pending.size)
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code()))
                    totalCount = 0
                    updatePageInfo(0, 0)
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    UiNotifier.show(this@CategoriesActivity, "Sin conexión a Internet")
                } else {
                    UiNotifier.show(this@CategoriesActivity, "Error de red: ${e.message}")
                }
                val pending = if (name == null && currentOffset == 0) buildPendingCategories() else emptyList()
                adapter.submit(pending)
                totalCount = pending.size
                updatePageInfo(pending.size, pending.size)
            } finally {
                isLoading = false
            }
        }
    }

    private fun createCategory() {
        val name = binding.etCategoryName.text.toString().trim()
        if (name.isBlank()) { binding.etCategoryName.error = "Nombre requerido"; return }

        binding.btnCreateCategory.isEnabled = false
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createCategory(CategoryCreateDto(name))
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    binding.etCategoryName.setText("")
                    currentOffset = 0
                    loadCategories()
                } else {
                    UiNotifier.show(this@CategoriesActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    OfflineQueue(this@CategoriesActivity).enqueue(PendingType.CATEGORY_CREATE, gson.toJson(CategoryCreateDto(name)))
                    UiNotifier.show(this@CategoriesActivity, "Sin conexión. Categoría guardada offline")
                    currentOffset = 0
                    loadCategories()
                } else {
                    UiNotifier.show(this@CategoriesActivity, "Error de red: ${e.message}")
                }
            } finally {
                binding.btnCreateCategory.isEnabled = true
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
                    currentOffset = 0
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
                    currentOffset = 0
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
                name = dto?.name ?: "Categoría offline",
                createdAt = "offline",
                updatedAt = null
            )
        }
    }

    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        if (searchId != null) {
            binding.tvCategoriesPageInfo.text = "Mostrando ${items.size} / ${items.size}"
            binding.btnPrevPageCategories.isEnabled = false
            binding.btnNextPageCategories.isEnabled = false
            applyPagerButtonStyle(binding.btnPrevPageCategories, enabled = false)
            applyPagerButtonStyle(binding.btnNextPageCategories, enabled = false)
            return
        }
        val shownOnline = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        val label = if (totalCount > 0) {
            "Mostrando $shownOnline / $totalCount"
        } else {
            "Mostrando $pendingCount / $pendingCount"
        }
        binding.tvCategoriesPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shownOnline < totalCount
        binding.btnPrevPageCategories.isEnabled = prevEnabled
        binding.btnNextPageCategories.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevPageCategories, prevEnabled)
        applyPagerButtonStyle(binding.btnNextPageCategories, nextEnabled)
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

    private fun toggleCreateForm() {
        TransitionManager.beginDelayedTransition(binding.scrollCategories, AutoTransition().setDuration(180))
        val isVisible = binding.layoutCreateCategoryContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutCreateCategoryContent.visibility = View.GONE
            binding.layoutSearchCategoryContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutCreateCategoryContent.visibility = View.VISIBLE
            binding.layoutSearchCategoryContent.visibility = View.GONE
            setToggleActive(binding.layoutCreateCategoryHeader)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollCategories, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchCategoryContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutSearchCategoryContent.visibility = View.GONE
            binding.layoutCreateCategoryContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutSearchCategoryContent.visibility = View.VISIBLE
            binding.layoutCreateCategoryContent.visibility = View.GONE
            setToggleActive(binding.layoutSearchCategoryHeader)
        }
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutCreateCategoryHeader) {
            binding.layoutCreateCategoryHeader.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.layoutSearchCategoryHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        } else if (active === binding.layoutSearchCategoryHeader) {
            binding.layoutCreateCategoryHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchCategoryHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutCreateCategoryHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchCategoryHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun applyCategoriesTitleGradient() {
        val title = binding.tvCategoriesTitle
        title.post {
            val paint = title.paint
            val width = paint.measureText(title.text.toString())
            if (width <= 0f) return@post
            val c1 = ContextCompat.getColor(this, R.color.icon_grad_start)
            val c2 = ContextCompat.getColor(this, R.color.icon_grad_mid2)
            val c3 = ContextCompat.getColor(this, R.color.icon_grad_mid1)
            val c4 = ContextCompat.getColor(this, R.color.icon_grad_end)
            val shader = android.graphics.LinearGradient(
                0f,
                0f,
                width,
                0f,
                intArrayOf(c1, c2, c3, c4),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            title.invalidate()
        }
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
