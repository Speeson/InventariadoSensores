package com.example.inventoryapp.ui.auth

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.example.inventoryapp.databinding.ActivityLoginTestBinding

class LoginTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackdropBlur()
        setupHintOnFocus(binding.etLoginTestEmail)
        setupHintOnFocus(binding.etLoginTestPassword)
        binding.loginTestRoot.apply {
            isFocusableInTouchMode = true
            setOnClickListener { clearInputFocus() }
        }
    }

    private fun setupBackdropBlur() {
        val windowBackground = window.decorView.background
        binding.layoutLoginPanel
            .setupWith(binding.blurTarget)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(3f)
            .setBlurAutoUpdate(true)
    }

    private fun setupHintOnFocus(view: View) {
        when (view) {
            binding.etLoginTestEmail -> {
                val originalHint = binding.etLoginTestEmail.hint
                binding.etLoginTestEmail.setOnFocusChangeListener { _, hasFocus ->
                    binding.etLoginTestEmail.hint = if (hasFocus) "" else originalHint
                }
            }
            binding.etLoginTestPassword -> {
                val originalHint = binding.etLoginTestPassword.hint
                binding.etLoginTestPassword.setOnFocusChangeListener { _, hasFocus ->
                    binding.etLoginTestPassword.hint = if (hasFocus) "" else originalHint
                }
            }
        }
    }

    private fun clearInputFocus() {
        currentFocus?.clearFocus()
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.loginTestRoot.requestFocus()
    }
}
