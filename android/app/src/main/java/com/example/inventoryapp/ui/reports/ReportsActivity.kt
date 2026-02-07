package com.example.inventoryapp.ui.reports
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import java.util.Calendar
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.databinding.ActivityReportsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.NetworkModule
import android.widget.ArrayAdapter
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.View
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivTopChevron, R.drawable.triangle_down_lg)
        GradientIconUtil.applyGradient(binding.ivTurnChevron, R.drawable.triangle_down_lg)
        applyReportsTitleGradient()
        applyHeaderGradients()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.layoutTopHeader.setOnClickListener { toggleTopSection() }
        binding.layoutTurnHeader.setOnClickListener { toggleTurnSection() }

        binding.btnTopConsumedSearch.setOnClickListener {
            val i = Intent(this, TopConsumedActivity::class.java)
            i.putExtra(EXTRA_LIMIT, binding.etTopLimit.text.toString().trim())
            i.putExtra(EXTRA_DATE_FROM, binding.etTopDateFrom.text.toString().trim())
            i.putExtra(EXTRA_DATE_TO, binding.etTopDateTo.text.toString().trim())
            i.putExtra(EXTRA_LOCATION, binding.etTopLocation.text.toString().trim())
            startActivity(i)
        }

        binding.btnTurnoverSearch.setOnClickListener {
            val i = Intent(this, TurnoverReportActivity::class.java)
            i.putExtra(EXTRA_LIMIT, binding.etTurnLimit.text.toString().trim())
            i.putExtra(EXTRA_DATE_FROM, binding.etTurnDateFrom.text.toString().trim())
            i.putExtra(EXTRA_DATE_TO, binding.etTurnDateTo.text.toString().trim())
            i.putExtra(EXTRA_LOCATION, binding.etTurnLocation.text.toString().trim())
            startActivity(i)
        }

        setupDatePickers()
        setupLocationDropdowns()
        binding.tilTopLocation.post { applyLocationDropdownIcons() }
    }

    private fun setupDatePickers() {
        bindDateField(binding.etTopDateFrom)
        bindDateField(binding.etTopDateTo)
        bindDateField(binding.etTurnDateFrom)
        bindDateField(binding.etTurnDateTo)
    }

    private fun bindDateField(field: android.widget.EditText) {
        field.setOnClickListener { showDatePicker(field) }
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDatePicker(field)
        }
    }

    private fun showDatePicker(target: android.widget.EditText) {
        val cal = Calendar.getInstance()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val mm = String.format("%02d", month + 1)
                val dd = String.format("%02d", dayOfMonth)
                target.setText("$year-$mm-$dd")
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Hoy") { _, _ ->
            val now = Calendar.getInstance()
            val mm = String.format("%02d", now.get(Calendar.MONTH) + 1)
            val dd = String.format("%02d", now.get(Calendar.DAY_OF_MONTH))
            target.setText("${now.get(Calendar.YEAR)}-$mm-$dd")
        }
        dialog.show()
    }

    private fun toggleTopSection() {
        TransitionManager.beginDelayedTransition(binding.scrollReports, AutoTransition().setDuration(180))
        val isVisible = binding.layoutTopContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutTopContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutTopContent.visibility = View.VISIBLE
            binding.layoutTurnContent.visibility = View.GONE
            setToggleActive(binding.layoutTopHeader)
        }
    }

    private fun toggleTurnSection() {
        TransitionManager.beginDelayedTransition(binding.scrollReports, AutoTransition().setDuration(180))
        val isVisible = binding.layoutTurnContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutTurnContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutTurnContent.visibility = View.VISIBLE
            binding.layoutTopContent.visibility = View.GONE
            setToggleActive(binding.layoutTurnHeader)
        }
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutTopHeader) {
            binding.layoutTopHeader.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.layoutTurnHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        } else if (active === binding.layoutTurnHeader) {
            binding.layoutTopHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutTurnHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutTopHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutTurnHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        }
    }

    private fun setupLocationDropdowns() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val values = items.sortedBy { it.id }
                        .map { "(${it.id}) ${it.code}" }
                        .distinct()
                    val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = ArrayAdapter(this@ReportsActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etTopLocation.setAdapter(adapter)
                    binding.etTopLocation.setOnClickListener { binding.etTopLocation.showDropDown() }
                    binding.etTopLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etTopLocation.showDropDown()
                    }
                    binding.etTurnLocation.setAdapter(adapter)
                    binding.etTurnLocation.setOnClickListener { binding.etTurnLocation.showDropDown() }
                    binding.etTurnLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etTurnLocation.showDropDown()
                    }
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
            }
        }
    }

    private fun applyLocationDropdownIcons() {
        binding.tilTopLocation.setEndIconTintList(null)
        binding.tilTurnLocation.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilTopLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
        binding.tilTurnLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
    }

    private fun applyReportsTitleGradient() {
        binding.tvReportsTitle.post {
            val paint = binding.tvReportsTitle.paint
            val width = paint.measureText(binding.tvReportsTitle.text.toString())
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
            binding.tvReportsTitle.invalidate()
        }
    }

    private fun applyHeaderGradients() {
        binding.tvTopHeader.post {
            val paint = binding.tvTopHeader.paint
            val width = paint.measureText(binding.tvTopHeader.text.toString())
            if (width > 0f) {
                val c1 = ContextCompat.getColor(this, R.color.icon_grad_start)
                val c2 = ContextCompat.getColor(this, R.color.icon_grad_mid2)
                val c3 = ContextCompat.getColor(this, R.color.icon_grad_mid1)
                val c4 = ContextCompat.getColor(this, R.color.icon_grad_end)
                paint.shader = android.graphics.LinearGradient(0f, 0f, width, 0f, intArrayOf(c1, c2, c3, c4), null, android.graphics.Shader.TileMode.CLAMP)
                binding.tvTopHeader.invalidate()
            }
        }
        binding.tvTurnHeader.post {
            val paint = binding.tvTurnHeader.paint
            val width = paint.measureText(binding.tvTurnHeader.text.toString())
            if (width > 0f) {
                val c1 = ContextCompat.getColor(this, R.color.icon_grad_start)
                val c2 = ContextCompat.getColor(this, R.color.icon_grad_mid2)
                val c3 = ContextCompat.getColor(this, R.color.icon_grad_mid1)
                val c4 = ContextCompat.getColor(this, R.color.icon_grad_end)
                paint.shader = android.graphics.LinearGradient(0f, 0f, width, 0f, intArrayOf(c1, c2, c3, c4), null, android.graphics.Shader.TileMode.CLAMP)
                binding.tvTurnHeader.invalidate()
            }
        }
    }

    companion object {
        const val EXTRA_LIMIT = "extra_limit"
        const val EXTRA_DATE_FROM = "extra_date_from"
        const val EXTRA_DATE_TO = "extra_date_to"
        const val EXTRA_LOCATION = "extra_location"
    }
}
