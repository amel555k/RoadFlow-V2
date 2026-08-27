package com.amko.roadflow.data.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RadarRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REFRESH) {
            RadarWorkScheduler.scheduleOneTime(context.applicationContext, expedited = true)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.amko.roadflow.ACTION_REFRESH"
    }
}