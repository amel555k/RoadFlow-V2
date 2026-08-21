package com.amko.roadflow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.amko.roadflow.data.local.CoordinateRepository
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.RadarConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RoadFlowApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        @Volatile
        var coordinatesReady: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "radar_status_channel",
                "Status Radara",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val coordinateRepository = CoordinateRepository(applicationContext, FirebaseService())
        appScope.launch {
            try {
                val loadedCoords = coordinateRepository.loadCoordinatesAsync()
                RadarConfig.coordinates = loadedCoords
                Log.d("BrzinaFetcha", "Momenat dodjele varijabli RadarConfig.coordinates, ucitano ${loadedCoords.size} koord")
            } catch (e: Exception) {
                Log.d("WidgetDebug", "RoadFlowApp: greška pri punjenju koordinata: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                coordinatesReady = true
            }
        }
    }
}