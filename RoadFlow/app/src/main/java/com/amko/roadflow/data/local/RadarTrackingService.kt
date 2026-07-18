package com.amko.roadflow.data.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.amko.roadflow.MainActivity
import com.amko.roadflow.R
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class RadarTrackingService : Service() {

    companion object {
        const val ACTION_START = "com.amko.roadflow.action.START_TRACKING"
        const val ACTION_STOP = "com.amko.roadflow.action.STOP_TRACKING"
        const val EXTRA_OPEN_MAP = "open_map"

        private const val FOREGROUND_CHANNEL_ID = "radar_tracking_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private var _locationService: LocationTrackingService? = null
        val locationService: LocationTrackingService
            get() {
                if (_locationService == null) {
                    _locationService = LocationTrackingService(sharedAppContext!!)
                }
                return _locationService!!
            }

        var alertService: RadarAlertService? = null
            private set

        private var sharedAppContext: Context? = null

        fun init(context: Context) {
            if (sharedAppContext == null) {
                sharedAppContext = context.applicationContext
            }
            if (_locationService == null) {
                _locationService = LocationTrackingService(sharedAppContext!!)
            }
            if (alertService == null) {
                alertService = RadarAlertService(sharedAppContext!!)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, RadarTrackingService::class.java).apply {
                action = ACTION_START
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RadarTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setActiveRadars(radars: List<RadarData>) {
            alertService?.setActiveRadars(radars)
        }
    }

    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        init(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (_isRunning.value) return
        _isRunning.value = true

        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())

        locationService.startActiveTracking()

        val scope = CoroutineScope(Dispatchers.Default + Job())
        serviceScope = scope

        scope.launch {
            locationService.location.collect { loc ->
                if (loc != null) {
                    alertService?.checkProximity(loc)
                }
            }
        }
    }

    private fun stopTracking() {
        _isRunning.value = false
        serviceScope?.cancel()
        serviceScope = null
        locationService.stopActiveTracking()
        alertService?.stopAlerts()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope?.cancel()
        serviceScope = null
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(FOREGROUND_CHANNEL_ID)
            if (existing != null && existing.importance == NotificationManager.IMPORTANCE_MIN) {
                manager.deleteNotificationChannel(FOREGROUND_CHANNEL_ID)
            }
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Praćenje vožnje",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Prikazuje status praćenja lokacije tokom vožnje"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_MAP, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("RoadFlow")
            .setContentText("Praćenje vožnje je aktivirano")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}