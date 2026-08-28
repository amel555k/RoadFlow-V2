package com.amko.roadflow.data.local

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.amko.roadflow.MainActivity
import com.amko.roadflow.R
import com.amko.roadflow.domain.model.RadarData
import java.time.format.DateTimeFormatter

object RadarNotificationHelper {

    const val NOTIFICATION_ID = 1001
    const val ACTION_NOTIFICATION_DELETED = "com.amko.roadflow.ACTION_NOTIFICATION_DELETED"
    const val CHANNEL_ID = "radar_status_channel"

    fun update(
        context: Context,
        currentRadars: List<RadarData>,
        isNoInternetNoCache: Boolean
    ) {
        val appContext = context.applicationContext
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val prefs = appContext.getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)
        val favoriteCity = prefs.getString("favorite_city", "") ?: ""

        if (favoriteCity.isBlank()) return

        val contentText: String
        val inboxStyle = NotificationCompat.InboxStyle()

        if (isNoInternetNoCache) {
            contentText = "Provjerite internet konekciju"
            inboxStyle.addLine("Podaci nisu dostupni bez interneta.")
            inboxStyle.addLine("Pritisnite 'Osvježi' kada uključite internet.")
        } else {
            val cityRadars = currentRadars.filter {
                it.city.equals(favoriteCity, ignoreCase = true) && it.time != "INFO"
            }

            val dataDate = cityRadars.firstOrNull()?.pageDate?.toLocalDate()
            val currentEffectiveDate = TimeProvider.effectiveRadarDate()
            val isDataStale = dataDate != null && dataDate != currentEffectiveDate

            contentText = if (cityRadars.isEmpty() || isDataStale) {
                "Danas nema radara"
            } else {
                "Danas ima radara, proširite obavijest za prikaz termina"
            }

            if (cityRadars.isNotEmpty() && !isDataStale) {
                cityRadars.forEach { radar ->
                    inboxStyle.addLine("${radar.time} - ${radar.location}")
                }
            }
        }

        val titleDateSuffix = if (!isNoInternetNoCache) {
            val cityRadarsForTitle = currentRadars.filter {
                it.city.equals(favoriteCity, ignoreCase = true) && it.time != "INFO"
            }
            val dataDate = cityRadarsForTitle.firstOrNull()?.pageDate?.toLocalDate()
            if (dataDate != null) " (${dataDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))})" else ""
        } else {
            ""
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getBroadcast(
            appContext, 2, Intent(ACTION_NOTIFICATION_DELETED).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(if (favoriteCity.isBlank()) "RoadFlow" else "$favoriteCity$titleDateSuffix")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)

        if (isNoInternetNoCache) {
            val refreshIntent = Intent(appContext, RadarRefreshReceiver::class.java).apply {
                action = RadarRefreshReceiver.ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                appContext, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.refresh, "Osvježi", refreshPendingIntent)
            builder.setStyle(inboxStyle)
        } else {
            val cityRadarsForExpand = currentRadars.filter {
                it.city.equals(favoriteCity, ignoreCase = true) && it.time != "INFO"
            }
            val expandDataDate = cityRadarsForExpand.firstOrNull()?.pageDate?.toLocalDate()
            val isExpandDataStale = expandDataDate != null && expandDataDate != TimeProvider.effectiveRadarDate()
            val hasExpandableContent = cityRadarsForExpand.isNotEmpty() && !isExpandDataStale
            if (hasExpandableContent) {
                builder.setStyle(inboxStyle)
            }
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun showLoading(context: Context, city: String, showRefresh: Boolean = false) {
        val appContext = context.applicationContext
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getBroadcast(
            appContext, 2, Intent(ACTION_NOTIFICATION_DELETED).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(if (city.isBlank()) "RoadFlow" else city)
            .setContentText("Učitavanje...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)

        if (showRefresh) {
            val refreshIntent = Intent(appContext, RadarRefreshReceiver::class.java).apply {
                action = RadarRefreshReceiver.ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                appContext, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.refresh, "Osvježi", refreshPendingIntent)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}