package com.amko.roadflow.data.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class RadarBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action || "android.intent.action.QUICKBOOT_POWERON" == action) {
            val prefs = context.getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("notification_enabled", false)

            if (enabled) {
                val serviceIntent = Intent(context, RadarNotificationService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}