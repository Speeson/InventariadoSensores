package com.example.inventoryapp

import android.app.Application
import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.AlertsWebSocketManager
import com.example.inventoryapp.data.remote.FcmTokenManager
import com.example.inventoryapp.ui.common.ActivityTracker
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import android.widget.TextView
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.data.local.OfflineSyncScheduler

class InventoryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        NetworkModule.forceOnline()
        AlertsWebSocketManager.connect(this)
        FcmTokenManager.sync(this)
        scheduleOfflineSync()
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ActivityTracker.setCurrent(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                ActivityTracker.setCurrent(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                ActivityTracker.setCurrent(activity)
                AlertsWebSocketManager.connect(this@InventoryApp)
                val badge = activity.findViewById<TextView>(R.id.tvAlertsBadge)
                val owner = activity as? LifecycleOwner
                if (badge != null && owner != null) {
                    AlertsBadgeUtil.refresh(owner.lifecycleScope, badge)
                }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun scheduleOfflineSync() {
        OfflineSyncScheduler.schedulePeriodic(this)
        OfflineSyncScheduler.scheduleOneTime(this)
    }
}
