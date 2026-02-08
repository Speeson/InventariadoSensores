package com.example.inventoryapp.ui.common

import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.NetworkModule

object NetworkStatusBar {
    fun bind(owner: LifecycleOwner, bar: View) {
        owner.lifecycleScope.launchWhenStarted {
            NetworkModule.offlineState.collect { offline ->
                val color = if (offline) {
                    R.color.offline_bar
                } else {
                    R.color.online_bar
                }
                bar.setBackgroundColor(ContextCompat.getColor(bar.context, color))
                bar.visibility = View.VISIBLE
            }
        }
    }
}
