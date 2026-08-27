package com.amko.roadflow.data.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RadarNotificationDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == RadarNotificationHelper.ACTION_NOTIFICATION_DELETED) {
            val appContext = context.applicationContext
            val firebaseService = FirebaseService()
            val parser = RadarParser(appContext, firebaseService)

            if (parser.isCachedForToday()) {
                kotlinx.coroutines.runBlocking {
                    val cached = parser.getActiveRadarsAsync()
                    RadarNotificationHelper.update(
                        context = appContext,
                        currentRadars = cached,
                        isNoInternetNoCache = false
                    )
                }
            } else {
                RadarWorkScheduler.scheduleOneTime(appContext)
            }
        }
    }
}