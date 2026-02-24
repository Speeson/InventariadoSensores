package com.example.inventoryapp.ui.profile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.databinding.ActivityProfileBinding
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        GradientIconUtil.applyGradient(binding.ivProfileIcon, R.drawable.user)
    }
}
