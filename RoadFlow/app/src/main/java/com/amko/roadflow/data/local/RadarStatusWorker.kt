package com.amko.roadflow.data.local

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amko.roadflow.MainActivity
import com.amko.roadflow.R
import com.amko.roadflow.domain.model.RadarData
import java.time.LocalTime

class RadarStatusWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notification_enabled", false)
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!enabled) {
            notificationManager.cancel(1001)
            return Result.success()
        }

        val favoriteCity = prefs.getString("favorite_city", "") ?: ""
        if (favoriteCity.isBlank()) {
            return Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val loadingNotification = NotificationCompat.Builder(applicationContext, "radar_status_channel")
            .setContentTitle(favoriteCity)
            .setContentText("Učitavanje...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, loadingNotification)

        val firebaseService = FirebaseService()
        val parser = RadarParser(applicationContext, firebaseService)

        var radars = emptyList<RadarData>()
        var noInternetNoCache = false

        try {
            parser.parseAllLocationsAsFlow(null).collect { list ->
                radars = list
            }
        } catch (e: com.amko.roadflow.data.local.NoInternetWithCacheException) {
            radars = e.cachedRadars
        } catch (e: Exception) {
            val cached = parser.getActiveRadarsAsync()
            if (cached.isEmpty()) {
                noInternetNoCache = true
            } else {
                radars = cached
            }
        }

        val contentText: String
        val inboxStyle = NotificationCompat.InboxStyle()

        if (noInternetNoCache) {
            contentText = "Provjerite internet konekciju"
            inboxStyle.addLine("Podaci nisu dostupni bez interneta.")

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val retryRequest = androidx.work.OneTimeWorkRequestBuilder<RadarStatusWorker>()
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork("RadarStatusRetry", androidx.work.ExistingWorkPolicy.REPLACE, retryRequest)
        } else {
            val cityRadars = radars.filter { it.city.equals(favoriteCity, ignoreCase = true) && it.time != "INFO" }
            val activeRadars = cityRadars.filter { isRadarActiveNow(it.time) }

            contentText = if (activeRadars.isEmpty()) {
                "Trenutno nema radara"
            } else {
                activeRadars.joinToString(", ") { it.location }
            }

            if (cityRadars.isEmpty()) {
                inboxStyle.addLine("Nema planiranih radara za ovaj dan.")
            } else {
                cityRadars.forEach { radar ->
                    inboxStyle.addLine("${radar.time} - ${radar.location}")
                }
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, "radar_status_channel")
            .setContentTitle(favoriteCity)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setStyle(inboxStyle)
            .build()

        notificationManager.notify(1001, notification)
        return Result.success()
    }
    private fun isRadarActiveNow(timeStr: String): Boolean {
        return try {
            val parts = timeStr.split(" do ")
            if (parts.size == 2) {
                val now = LocalTime.now()
                val start = LocalTime.parse(parts[0].trim())
                val end = LocalTime.parse(parts[1].trim())
                if (end.isBefore(start)) {
                    !now.isBefore(start) || !now.isAfter(end)
                } else {
                    !now.isBefore(start) && !now.isAfter(end)
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}