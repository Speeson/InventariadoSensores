package com.example.inventoryapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.OfflineSyncScheduler
import com.example.inventoryapp.data.remote.AlertsWebSocketManager
import com.example.inventoryapp.data.remote.FcmTokenManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.ui.common.ActivityTracker
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.ui.common.LiquidBottomNav
import com.example.inventoryapp.ui.common.LiquidTopNav

class InventoryApp : Application() {

    companion object {
        private const val DIAG_TAG = "NavDiag"
        private const val DIAG_ENABLED = true
    }

    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        NetworkModule.forceOnline()
        NetworkModule.startHealthPing()
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
                (activity as? AppCompatActivity)?.let {
                    kotlin.runCatching { LiquidBottomNav.install(it) }
                    kotlin.runCatching { LiquidTopNav.install(it) }
                    diagState(it, "onCreated_afterInstall")
                }
            }

            override fun onActivityStarted(activity: Activity) {
                ActivityTracker.setCurrent(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                ActivityTracker.setCurrent(activity)
                (activity as? AppCompatActivity)?.let {
                    diagState(it, "onResumed_beforeInstall")
                    kotlin.runCatching { LiquidBottomNav.install(it) }
                    kotlin.runCatching { LiquidTopNav.install(it) }
                    diagState(it, "onResumed_afterInstall")
                    it.window?.decorView?.post {
                        diagState(it, "onResumed_post")
                    }
                    it.window?.decorView?.postDelayed({
                        diagState(it, "onResumed_postDelayed120")
                    }, 120L)
                }
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

    private fun diagState(activity: AppCompatActivity, step: String) {
        if (!DIAG_ENABLED) return
        try {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            val childCount = content.childCount
            val primary = resolvePrimaryContentRoot(content)
            val actual = resolveActualContent(primary)
            val nav = content.findViewById<View>(R.id.liquidBottomNavRoot)
            val overlay = content.findViewById<View>(R.id.liquid_center_dismiss_overlay)

            Log.d(
                DIAG_TAG,
                "step=$step act=${activity.javaClass.simpleName} content(h=${content.height},w=${content.width},children=$childCount) " +
                    "primary=${viewInfo(primary)} actual=${viewInfo(actual)} nav=${viewInfo(nav)} overlay=${viewInfo(overlay)}"
            )

            for (i in 0 until childCount) {
                val child = content.getChildAt(i)
                Log.d(DIAG_TAG, "step=$step act=${activity.javaClass.simpleName} contentChild[$i]=${viewInfo(child)}")
            }
        } catch (t: Throwable) {
            Log.w(DIAG_TAG, "diag failed at step=$step in ${activity.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun resolvePrimaryContentRoot(content: ViewGroup): View? {
        val drawer = content.findViewById<View>(R.id.liquidGlobalDrawerLayout)
        if (drawer != null) return drawer

        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.id == R.id.liquidBottomNavRoot) continue
            if (child.id == R.id.liquid_center_dismiss_overlay) continue
            if (child.id == R.id.profileExpandPanel) continue
            return child
        }
        return null
    }

    private fun resolveActualContent(root: View?): View? {
        if (root is DrawerLayout) {
            val contentContainer = root.findViewById<ViewGroup>(R.id.liquidTopDrawerContentContainer)
            return contentContainer?.getChildAt(0)
        }
        return root
    }

    private fun viewInfo(view: View?): String {
        if (view == null) return "null"
        val idName = try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Exception) {
            view.id.toString()
        }
        return "id=$idName cls=${view.javaClass.simpleName} vis=${view.visibility} a=${"%.2f".format(view.alpha)} " +
            "x=${view.left},y=${view.top},w=${view.width},h=${view.height} " +
            "pL=${view.paddingLeft},pT=${view.paddingTop},pR=${view.paddingRight},pB=${view.paddingBottom}"
    }

    private fun scheduleOfflineSync() {
        OfflineSyncScheduler.schedulePeriodic(this)
        OfflineSyncScheduler.scheduleOneTime(this)
    }
}
