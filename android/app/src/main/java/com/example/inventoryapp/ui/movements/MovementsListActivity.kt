package com.example.inventoryapp.ui.movements
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementAdjustOperationRequest
import com.example.inventoryapp.data.remote.model.MovementOperationRequest
import com.example.inventoryapp.data.remote.model.MovementSourceDto
import com.example.inventoryapp.data.remote.model.MovementTransferOperationRequest
import com.example.inventoryapp.data.remote.model.MovementTypeDto
import com.example.inventoryapp.databinding.ActivityMovementsListBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.google.gson.Gson
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R

class MovementsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovementsListBinding
    private lateinit var snack: SendSnack

    private val gson = Gson()
    private lateinit var adapter: MovementsListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
snack = SendSnack(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = MovementsListAdapter()
        binding.rvMovements.layoutManager = LinearLayoutManager(this)
        binding.rvMovements.adapter = adapter

        setupTypeDropdown()
        setupSourceDropdown()

        binding.btnApplyFilters.setOnClickListener { loadMovements(withSnack = true) }
        binding.btnClearFilters.setOnClickListener { clearFilters() }
        binding.btnRefresh.setOnClickListener { loadMovements(withSnack = true) }
    }

    override fun onResume() {
        super.onResume()
        loadMovements(withSnack = false)
    }

    private fun loadMovements(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando movimientos...")

        val productId = binding.etProductId.text.toString().trim().toIntOrNull()
        val type = parseType(binding.etMovementType.text.toString())
        val source = parseSource(binding.etSource.text.toString())

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listMovements(
                    productId = productId,
                    movementType = type,
                    movementSource = source,
                    userId = null,
                    dateFrom = null,
                    dateTo = null,
                    limit = 100,
                    offset = 0
                )
                if (res.isSuccessful && res.body() != null) {
                    val remoteItems = res.body()!!.items
                    val pendingProductIds = collectPendingProductIds()
                    val allProductIds = mutableSetOf<Int>()
                    remoteItems.forEach { allProductIds.add(it.productId) }
                    allProductIds.addAll(pendingProductIds)
                    val productNames = fetchProductNames(allProductIds)

                    val remote = remoteItems.map { item ->
                        val loc = item.location ?: item.locationId?.toString() ?: "n/a"
                        val productName = productNames[item.productId] ?: "Producto ${item.productId}"
                        val qtyLabel = "Cantidad=${item.quantity}"
                        val deltaLabel = item.delta?.let { "Delta=$it" }
                        val titleLabel = if (item.movementType == MovementTypeDto.ADJUST && deltaLabel != null) {
                            deltaLabel
                        } else {
                            qtyLabel
                        }
                        MovementRow(
                            title = "${item.movementType}  •  $titleLabel",
                            meta = "Producto: $productName  •  Loc ${loc}",
                            sub = "Src ${item.movementSource}  •  ${item.createdAt}",
                            isPending = false,
                            type = item.movementType.name
                        )
                    }
                    val pending = buildPendingRows(productNames)
                    adapter.submit(pending + remote)
                    if (withSnack) snack.showSuccess("Movimientos cargados")
                } else {
                    if (withSnack) snack.showError("Error ${res.code()}: ${res.errorBody()?.string()}")
                    val productNames = fetchProductNames(collectPendingProductIds().toSet())
                    val pending = buildPendingRows(productNames)
                    adapter.submit(pending)
                }
            } catch (e: Exception) {
                if (withSnack) snack.showError("Error de red: ${e.message}")
                val productNames = fetchProductNames(collectPendingProductIds().toSet())
                val pending = buildPendingRows(productNames)
                adapter.submit(pending)
            }
        }
    }

    private fun buildPendingRows(productNames: Map<Int, String>): List<MovementRow> {
        val queue = OfflineQueue(this)
        val pending = queue.getAll()
        val out = mutableListOf<MovementRow>()

        pending.forEach { p ->
            when (p.type) {
                PendingType.MOVEMENT_IN -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        val productName = productNames[dto.productId] ?: "Producto ${dto.productId}"
                        out.add(
                            MovementRow(
                                title = "IN  •  Cantidad=${dto.quantity}",
                                meta = "Producto: $productName  •  Loc ${dto.location}",
                                sub = "Src ${dto.movementSource}  •  offline",
                                isPending = true,
                                type = "IN"
                            )
                        )
                    } else {
                        out.add(
                            MovementRow(
                                title = "IN  •  payload invalido",
                                meta = "offline",
                                sub = "pendiente",
                                isPending = true,
                                type = "IN"
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_OUT -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        val productName = productNames[dto.productId] ?: "Producto ${dto.productId}"
                        out.add(
                            MovementRow(
                                title = "OUT  •  Cantidad=${dto.quantity}",
                                meta = "Producto: $productName  •  Loc ${dto.location}",
                                sub = "Src ${dto.movementSource}  •  offline",
                                isPending = true,
                                type = "OUT"
                            )
                        )
                    } else {
                        out.add(
                            MovementRow(
                                title = "OUT  •  payload invalido",
                                meta = "offline",
                                sub = "pendiente",
                                isPending = true,
                                type = "OUT"
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_ADJUST -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementAdjustOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        val productName = productNames[dto.productId] ?: "Producto ${dto.productId}"
                        out.add(
                            MovementRow(
                                title = "ADJUST  •  Cantidad=${dto.delta}",
                                meta = "Producto: $productName  •  Loc ${dto.location}",
                                sub = "Src ${dto.movementSource}  •  offline",
                                isPending = true,
                                type = "ADJUST"
                            )
                        )
                    } else {
                        out.add(
                            MovementRow(
                                title = "ADJUST  •  payload invalido",
                                meta = "offline",
                                sub = "pendiente",
                                isPending = true,
                                type = "ADJUST"
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_TRANSFER -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementTransferOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        val productName = productNames[dto.productId] ?: "Producto ${dto.productId}"
                        out.add(
                            MovementRow(
                                title = "TRANSFER  •  Cantidad=${dto.quantity}",
                                meta = "Producto: $productName  •  ${dto.fromLocation} -> ${dto.toLocation}",
                                sub = "Src ${dto.movementSource}  •  offline",
                                isPending = true,
                                type = "TRANSFER"
                            )
                        )
                    } else {
                        out.add(
                            MovementRow(
                                title = "TRANSFER  •  payload invalido",
                                meta = "offline",
                                sub = "pendiente",
                                isPending = true,
                                type = "TRANSFER"
                            )
                        )
                    }
                }
                else -> Unit
            }
        }

        return out
    }

    private fun collectPendingProductIds(): List<Int> {
        val queue = OfflineQueue(this)
        val pending = queue.getAll()
        val out = mutableListOf<Int>()
        pending.forEach { p ->
            when (p.type) {
                PendingType.MOVEMENT_IN,
                PendingType.MOVEMENT_OUT -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementOperationRequest::class.java) }.getOrNull()
                    if (dto != null) out.add(dto.productId)
                }
                PendingType.MOVEMENT_ADJUST -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementAdjustOperationRequest::class.java) }.getOrNull()
                    if (dto != null) out.add(dto.productId)
                }
                PendingType.MOVEMENT_TRANSFER -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementTransferOperationRequest::class.java) }.getOrNull()
                    if (dto != null) out.add(dto.productId)
                }
                else -> Unit
            }
        }
        return out
    }

    private suspend fun fetchProductNames(ids: Set<Int>): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        ids.forEach { id ->
            try {
                val res = NetworkModule.api.getProduct(id)
                if (res.isSuccessful && res.body() != null) {
                    out[id] = res.body()!!.name
                }
            } catch (_: Exception) {
                // Keep fallback labels if lookup fails.
            }
        }
        return out
    }

    private fun setupTypeDropdown() {
        val values = listOf("", "IN", "OUT", "ADJUST")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, values)
        binding.etMovementType.setAdapter(adapter)
        binding.etMovementType.setOnItemClickListener { _, _, position, _ ->
            if (values[position].isBlank()) binding.etMovementType.setText("", false)
        }
        binding.etMovementType.setOnClickListener { binding.etMovementType.showDropDown() }
        binding.etMovementType.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etMovementType.showDropDown()
        }
    }

    private fun setupSourceDropdown() {
        val values = listOf("", "MANUAL", "SCAN")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, values)
        binding.etSource.setAdapter(adapter)
        binding.etSource.setOnItemClickListener { _, _, position, _ ->
            if (values[position].isBlank()) binding.etSource.setText("", false)
        }
        binding.etSource.setOnClickListener { binding.etSource.showDropDown() }
        binding.etSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSource.showDropDown()
        }
    }

    private fun parseType(raw: String): MovementTypeDto? {
        return when (raw.trim().uppercase()) {
            "IN" -> MovementTypeDto.IN
            "OUT" -> MovementTypeDto.OUT
            "ADJUST" -> MovementTypeDto.ADJUST
            else -> null
        }
    }

    private fun parseSource(raw: String): MovementSourceDto? {
        return when (raw.trim().uppercase()) {
            "MANUAL" -> MovementSourceDto.MANUAL
            "SCAN" -> MovementSourceDto.SCAN
            else -> null
        }
    }

    private fun clearFilters() {
        binding.etMovementType.setText("", false)
        binding.etSource.setText("", false)
        binding.etProductId.setText("")
        loadMovements(withSnack = false)
    }

}
