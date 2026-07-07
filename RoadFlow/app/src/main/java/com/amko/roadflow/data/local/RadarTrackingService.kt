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

        private const val FOREGROUND_CHANNEL_ID = "radar_tracking_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        var locationService: LocationTrackingService? = null
            private set

        var alertService: RadarAlertService? = null
            private set

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
        locationService = LocationTrackingService(applicationContext)
        alertService = RadarAlertService(applicationContext)
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

        locationService?.startActiveTracking()

        val scope = CoroutineScope(Dispatchers.Default + Job())
        serviceScope = scope

        scope.launch {
            locationService?.location?.collect { loc ->
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
        locationService?.stopActiveTracking()
        alertService?.stopAlerts()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope?.cancel()
        serviceScope = null
        locationService?.dispose()
        alertService?.dispose()
        locationService = null
        alertService = null
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Praćenje vožnje",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Prikazuje status praćenja lokacije tokom vožnje"
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("RoadFlow")
            .setContentText("Aktivno praćenje vožnje je aktivno")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }
}