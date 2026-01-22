package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.databinding.ActivityResultBinding
import com.example.inventoryapp.ui.scan.ScanActivity

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val success = intent.getBooleanExtra("success", false)
        val msg = intent.getStringExtra("msg").orEmpty()

        binding.tvTitle.text = if (success) "✅ Éxito" else "❌ Error"
        binding.tvMsg.text = msg

        binding.btnBack.setOnClickListener {
            val i = Intent(this, ScanActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        }
    }
}
