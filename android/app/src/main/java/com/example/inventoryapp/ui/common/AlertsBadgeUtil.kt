package com.example.inventoryapp.ui.common

import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import kotlinx.coroutines.launch

object AlertsBadgeUtil {
    fun refresh(scope: LifecycleCoroutineScope, badge: TextView) {
        scope.launch {
            try {
                val res = NetworkModule.api.listAlerts(
                    status = AlertStatusDto.PENDING,
                    limit = 1,
                    offset = 0
                )
                if (!res.isSuccessful || res.body() == null) {
                    badge.visibility = View.GONE
                    return@launch
                }
                val total = res.body()!!.total
                if (total > 0) {
                    badge.text = if (total > 99) "99+" else total.toString()
                    badge.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                }
            } catch (_: Exception) {
                badge.visibility = View.GONE
            }
        }
    }
}
