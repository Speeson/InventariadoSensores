package com.example.inventoryapp.ui.reports

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import java.util.Calendar
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.databinding.ActivityReportsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

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

    companion object {
        const val EXTRA_LIMIT = "extra_limit"
        const val EXTRA_DATE_FROM = "extra_date_from"
        const val EXTRA_DATE_TO = "extra_date_to"
        const val EXTRA_LOCATION = "extra_location"
    }
}
