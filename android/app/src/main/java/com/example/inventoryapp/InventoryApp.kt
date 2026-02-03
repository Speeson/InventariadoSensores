package com.example.inventoryapp

import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.ui.common.ActivityTracker

class InventoryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ActivityTracker.setCurrent(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                ActivityTracker.setCurrent(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                ActivityTracker.setCurrent(activity)
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
