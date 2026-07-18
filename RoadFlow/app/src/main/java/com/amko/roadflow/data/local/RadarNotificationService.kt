package com.amko.roadflow.data.local

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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

        if (intent?.action == "ACTION_REFRESH") {
            startForeground(1001, createLoadingNotification(favoriteCity))
            serviceScope.launch { fetchData() }
            return START_STICKY
        }

        startForeground(1001, createLoadingNotification(favoriteCity))

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

            val timeoutJob = serviceScope.launch {
                delay(5000)
                if (isFetching) {
                    withContext(Dispatchers.Main) {
                        notificationManager.notify(1001, createLoadingNotification(favoriteCity, showRefresh = true))
                    }
                }
            }

            val firebaseService = FirebaseService()
            val parser = RadarParser(applicationContext, firebaseService)

            try {
                parser.parseAllLocationsAsFlow(null).collect { list ->
                    currentRadars = list
                    isNoInternetNoCache = false
                }
            } catch (e: NoInternetWithCacheException) {
                currentRadars = e.cachedRadars
                isNoInternetNoCache = false
            } catch (e: Exception) {
                val cached = parser.getActiveRadarsAsync()
                if (cached.isEmpty()) {
                    isNoInternetNoCache = true
                    currentRadars = emptyList()
                } else {
                    currentRadars = cached
                    isNoInternetNoCache = false
                }
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
            inboxStyle.addLine("Čeka se konekcija da se osvježi...")
        } else {
            val cityRadars = currentRadars.filter { it.city.equals(favoriteCity, ignoreCase = true) && it.time != "INFO" }
            val activeRadars = cityRadars.filter { isRadarActiveNow(it.time) }

            contentText = if (activeRadars.isEmpty()) {
                val now = LocalTime.now()
                val nextStart = cityRadars
                    .mapNotNull { parseStartTime(it.time) }
                    .filter { it.isAfter(now) }
                    .minOrNull()

                if (nextStart != null) {
                    "Trenutno nema radara do ${nextStart.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                } else {
                    "Danas više nema radara"
                }
            } else {
                val activeUntil = activeRadars.mapNotNull { parseEndTime(it.time) }.minOrNull()
                val prefix = activeRadars.joinToString(", ") { it.location }
                if (activeUntil != null) {
                    "$prefix (do ${activeUntil.format(DateTimeFormatter.ofPattern("HH:mm"))})"
                } else {
                    prefix
                }
            }

            if (cityRadars.isEmpty()) {
                inboxStyle.addLine("Nema planiranih radara za ovaj dan.")
            } else {
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

        val notification = NotificationCompat.Builder(applicationContext, "radar_status_channel")
            .setContentTitle(favoriteCity)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setStyle(inboxStyle)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

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

        val builder = NotificationCompat.Builder(applicationContext, "radar_status_channel")
            .setContentTitle(city)
            .setContentText("Učitavanje...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
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

    private fun parseStartTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.split(" do ")
            if (parts.size == 2) {
                LocalTime.parse(parts[0].trim())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEndTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.split(" do ")
            if (parts.size == 2) {
                LocalTime.parse(parts[1].trim())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (e: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}