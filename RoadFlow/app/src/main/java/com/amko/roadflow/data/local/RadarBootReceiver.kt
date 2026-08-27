package com.amko.roadflow.data.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

/**
 * Reaguje na BOOT_COMPLETED / QUICKBOOT_POWERON.
 * - Ako postoji keš za danas -> odmah iscrtaj notifikaciju (potpuno offline).
 * - Ako nema keša -> zakaži WorkManager da fetch-uje čim internet postane dostupan.
 */
class RadarBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED != action && "android.intent.action.QUICKBOOT_POWERON" != action) {
            return
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notification_enabled", false)

        if (!enabled) return

        val favoriteCity = prefs.getString("favorite_city", "") ?: ""
        if (favoriteCity.isBlank()) return

        val firebaseService = FirebaseService()
        val parser = RadarParser(appContext, firebaseService)

        if (parser.isCachedForToday()) {
            runBlocking {
                val cached = parser.getActiveRadarsAsync()
                RadarNotificationHelper.update(
                    context = appContext,
                    currentRadars = cached,
                    isNoInternetNoCache = false
                )
            }
        } else {
            RadarNotificationHelper.showLoading(appContext, favoriteCity)
        }

        RadarWorkScheduler.schedulePeriodic(appContext)
        RadarWorkScheduler.scheduleOneTime(appContext)
    }
}