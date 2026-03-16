package com.example.inventoryapp.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.inventoryapp.R
import com.example.inventoryapp.ui.alerts.UrgentAlertsRepository
import kotlinx.coroutines.launch

object AlertsBadgeUtil {
    fun refresh(scope: LifecycleCoroutineScope, badge: TextView) {
        val activity = badge.context.findActivity()
        if (activity == null) {
            badge.visibility = View.GONE
            return
        }
        scope.launch {
            try {
                val total = UrgentAlertsRepository.load(activity).size
                applyCount(activity, total)
            } catch (_: Exception) {
                applyCount(activity, 0)
            }
        }
    }

    fun applyCount(badge: TextView?, total: Int) {
        if (badge == null) return
        if (total > 0) {
            badge.text = if (total > 99) "99+" else total.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    fun applyCount(activity: AppCompatActivity, total: Int) {
        findBadges(activity).forEach { applyCount(it, total) }
    }

    private fun findBadges(activity: AppCompatActivity): List<TextView> {
        val root = activity.window?.decorView as? ViewGroup ?: return emptyList()
        val result = mutableListOf<TextView>()
        collectBadges(root, result)
        return result.distinct()
    }

    private fun collectBadges(view: View, result: MutableList<TextView>) {
        if (view.id == R.id.tvAlertsBadge && view is TextView) {
            result.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectBadges(view.getChildAt(i), result)
            }
        }
    }

    private fun Context.findActivity(): AppCompatActivity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is AppCompatActivity) return current
            current = current.baseContext
        }
        return null
    }
}
