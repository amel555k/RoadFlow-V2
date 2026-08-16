package com.amko.roadflow.data.local

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.amko.roadflow.MainActivity
import com.amko.roadflow.R
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RadarNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private var isFetching = false

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var currentRadars = emptyList<RadarData>()
    private var isNoInternetNoCache = false

    private val notificationDeleteReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            serviceScope.launch {
                updateNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val filter = IntentFilter(ACTION_NOTIFICATION_DELETED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationDeleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationDeleteReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (RadarTrackingService.isRunning.value) {
            startForeground(1002, createLoadingNotification(""))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)
        val favoriteCity = prefs.getString("favorite_city", "") ?: ""

        val parser = RadarParser(applicationContext, FirebaseService())
        val isCachedToday = parser.isCachedForToday()

        if (!isCachedToday) {
            startForeground(1001, createLoadingNotification(favoriteCity))
        }

        if (intent?.action == "ACTION_REFRESH") {
            if (!isCachedToday) {
                startForeground(1001, createLoadingNotification(favoriteCity))
            }
            serviceScope.launch { fetchData() }
            return START_STICKY
        }

        if (intent?.action == "UPDATE_CITY") {
            serviceScope.coroutineContext.cancelChildren()
        }

        setupNetworkListener()
        startPeriodicUpdates()

        return START_STICKY
    }

    private fun setupNetworkListener() {
        if (networkCallback != null) return

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                serviceScope.launch {
                    fetchData()
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    private fun startPeriodicUpdates() {
        serviceScope.launch {
            while (isActive) {
                fetchData()
                delay(15 * 60 * 1000L)
            }
        }

        serviceScope.launch {
            while (isActive) {
                delay(60 * 1000L)
                updateNotification()
            }
        }
    }

    private suspend fun fetchData() {
        if (isFetching) return
        isFetching = true

        val prefs = getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)
        val favoriteCity = prefs.getString("favorite_city", "") ?: ""

        if (favoriteCity.isBlank()) {
            isFetching = false
            return
        }

        val firebaseService = FirebaseService()
        val parser = RadarParser(applicationContext, firebaseService)

        val isCachedToday = parser.isCachedForToday()
        if (isCachedToday) {
            val cached = parser.getActiveRadarsAsync()
            currentRadars = cached
            isNoInternetNoCache = false
            updateNotification()
        }

        val timeoutJob = serviceScope.launch {
            delay(30_000L)
            if (isFetching && !isCachedToday) {
                withContext(Dispatchers.Main) {
                    notificationManager.notify(1001, createLoadingNotification(favoriteCity, showRefresh = true))
                }
            }
        }

        try {
            withTimeout(30_000L) {
                currentRadars = parser.parseAllLocationsAsFlow(null).first().radars
                isNoInternetNoCache = false
            }
        } catch (e: NoInternetWithCacheException) {
            currentRadars = if (parser.isCachedForToday()) e.cachedRadars else emptyList()
            isNoInternetNoCache = currentRadars.isEmpty()
        } catch (e: TimeoutCancellationException) {
            val cached = if (parser.isCachedForToday()) parser.getActiveRadarsAsync() else emptyList()
            isNoInternetNoCache = cached.isEmpty()
            currentRadars = cached
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val cached = if (parser.isCachedForToday()) parser.getActiveRadarsAsync() else emptyList()
            isNoInternetNoCache = cached.isEmpty()
            currentRadars = cached
        }

        timeoutJob.cancel()
        updateNotification()
        isFetching = false
    }

    private suspend fun updateNotification() {
        val prefs = getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)
        val favoriteCity = prefs.getString("favorite_city", "") ?: ""

        if (favoriteCity.isBlank()) return

        val contentText: String
        val inboxStyle = NotificationCompat.InboxStyle()

        if (isNoInternetNoCache) {
            contentText = "Provjerite internet konekciju"
            inboxStyle.addLine("Podaci nisu dostupni bez interneta.")
            inboxStyle.addLine("Pritisnite 'Osvježi' kada uključite internet.")
        } else {
            val cityRadars = currentRadars.filter { it.city.equals(favoriteCity, ignoreCase = true) && it.time != "INFO" }
            val activeRadars = cityRadars.filter { isRadarActiveNow(it.time) }
            val now = LocalTime.now().withNano(0)

            contentText = if (cityRadars.isEmpty()) {
                "Danas nema planiranih radara."
            } else if (activeRadars.isEmpty()) {
                val nextStart = cityRadars
                    .mapNotNull { parseTimeRange(it.time)?.first }
                    .filter { it.isAfter(now) }
                    .minOrNull()

                if (nextStart != null) {
                    "Trenutno nema radara do ${nextStart.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                } else {
                    "Danas više nema radara"
                }
            } else {
                val activeUntil = activeRadars.mapNotNull { parseTimeRange(it.time)?.second }.minOrNull()
                val prefix = activeRadars.joinToString(", ") { it.location }
                if (activeUntil != null) {
                    "$prefix (do ${activeUntil.format(DateTimeFormatter.ofPattern("HH:mm"))})"
                } else {
                    prefix
                }
            }

            if (cityRadars.isNotEmpty()) {
                cityRadars.forEach { radar ->
                    inboxStyle.addLine("${radar.time} - ${radar.location}")
                }
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getBroadcast(
            applicationContext, 2, Intent(ACTION_NOTIFICATION_DELETED).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, "radar_status_channel")
            .setContentTitle(if (favoriteCity.isBlank()) "RoadFlow" else favoriteCity)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification1)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (isNoInternetNoCache) {
            val refreshIntent = Intent(applicationContext, RadarNotificationService::class.java).apply {
                action = "ACTION_REFRESH"
            }
            val refreshPendingIntent = PendingIntent.getService(
                applicationContext, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.refresh, "Osvježi", refreshPendingIntent)
            builder.setStyle(inboxStyle)
        } else {
            val hasExpandableContent = currentRadars.any { it.city.equals(favoriteCity, ignoreCase = true) && it.time != "INFO" }
            if (hasExpandableContent) {
                builder.setStyle(inboxStyle)
            }
        }

        val notification = builder.build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1001, notification)
        } else {
            startForeground(1001, notification)
        }

        withContext(Dispatchers.Main) {
            notificationManager.notify(1001, notification)
        }
    }

    private fun createLoadingNotification(city: String, showRefresh: Boolean = false): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getBroadcast(
            applicationContext, 2, Intent(ACTION_NOTIFICATION_DELETED).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, "radar_status_channel")
            .setContentTitle(if (city.isBlank()) "RoadFlow" else city)
            .setContentText("Učitavanje...")
            .setSmallIcon(R.drawable.ic_notification1)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (showRefresh) {
            val refreshIntent = Intent(applicationContext, RadarNotificationService::class.java).apply {
                action = "ACTION_REFRESH"
            }
            val refreshPendingIntent = PendingIntent.getService(
                applicationContext, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.refresh, "Osvježi", refreshPendingIntent)
        }

        return builder.build()
    }

    private fun parseTimeRange(timeStr: String): Pair<LocalTime, LocalTime>? {
        if (timeStr.isBlank() || timeStr.equals("INFO", ignoreCase = true)) return null
        val normalized = timeStr.replace("–", "-").replace("—", "-").trim()
        val delimiter = when {
            normalized.contains(" do ") -> " do "
            normalized.contains(" DO ") -> " DO "
            normalized.contains("-") -> "-"
            else -> return null
        }
        val parts = normalized.split(delimiter)
        if (parts.size != 2) return null
        val start = parseSingleTime(parts[0]) ?: return null
        val end = parseSingleTime(parts[1]) ?: return null
        return Pair(start, end)
    }

    private fun parseSingleTime(str: String): LocalTime? {
        val cleanStr = str.trim()
        val formatters = arrayOf(
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm:ss")
        )
        for (formatter in formatters) {
            try {
                return LocalTime.parse(cleanStr, formatter)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun isRadarActiveNow(timeStr: String): Boolean {
        val range = parseTimeRange(timeStr) ?: return false
        val now = LocalTime.now().withNano(0)
        val start = range.first
        val end = range.second

        return if (end.isBefore(start)) {
            !now.isBefore(start) || !now.isAfter(end)
        } else {
            !now.isBefore(start) && !now.isAfter(end)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (e: Exception) {}
        }
        try { unregisterReceiver(notificationDeleteReceiver) } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_NOTIFICATION_DELETED = "com.amko.roadflow.ACTION_NOTIFICATION_DELETED"
    }
}