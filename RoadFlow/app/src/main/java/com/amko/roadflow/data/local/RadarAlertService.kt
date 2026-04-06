package com.amko.roadflow.data.local

import android.content.Context
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.location.Location
import com.amko.roadflow.R
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask

class RadarAlertService(private val context: Context) {

    companion object {
        const val ALERT_RADIUS_METERS = 200.0
    }

    private var activeRadars: List<RadarData> = emptyList()
    private var isInsideZone = false
    private var currentSpeedLimit = 0
    private var lastSpeedKmh = 0.0
    private var alertTimer: Timer? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _speedLimit = MutableStateFlow(0)
    val speedLimit: StateFlow<Int> = _speedLimit.asStateFlow()

    private val _isInZone = MutableStateFlow(false)
    val isInZone: StateFlow<Boolean> = _isInZone.asStateFlow()

    init {
        prepareAudio()
    }

    private fun prepareAudio() {
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.beep)
            mediaPlayer?.isLooping = false
        } catch (e: Exception) {
            android.util.Log.e("RadarAlertService", "Greška pri pripremi audio: ${e.message}")
        }
    }

    fun setActiveRadars(radars: List<RadarData>) {
        activeRadars = radars
    }

    fun checkProximity(location: Location) {
        if (activeRadars.isEmpty()) {
            if (isInsideZone) exitZone()
            return
        }

        val nearestRadar = activeRadars
            .filter { it.latitude != null && it.longitude != null }
            .map { radar ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude, location.longitude,
                    radar.latitude!!, radar.longitude!!,
                    results
                )
                Pair(radar, results[0].toDouble())
            }
            .filter { (_, distance) -> distance <= ALERT_RADIUS_METERS }
            .minByOrNull { (_, distance) -> distance }

        if (nearestRadar != null) {
            val (radar, _) = nearestRadar
            val speedKmh = (location.speed * 3.6)
            val speedLimit = radar.speedLimit ?: radar.coordinate?.speedLimit ?: 0

            if (!isInsideZone) {
                enterZone(speedLimit, speedKmh)
            } else {
                val newInterval = getAlertInterval(speedKmh, currentSpeedLimit)
                val currentInterval = getAlertInterval(lastSpeedKmh, currentSpeedLimit)
                if (newInterval != currentInterval) {
                    lastSpeedKmh = speedKmh
                    startAlertLoop(speedKmh)
                }
            }
        } else if (isInsideZone) {
            exitZone()
        }
    }

    private fun getAlertInterval(speedKmh: Double, speedLimit: Int): Int {
        if (speedLimit <= 0) return 3
        val over = speedKmh - speedLimit
        return when {
            over >= 10 -> 1
            over >= 5  -> 2
            else       -> 3
        }
    }

    private fun enterZone(speedLimit: Int, speedKmh: Double) {
        isInsideZone = true
        currentSpeedLimit = speedLimit
        lastSpeedKmh = speedKmh
        _isInZone.value = true
        _speedLimit.value = speedLimit
        startAlertLoop(speedKmh)
    }

    private fun exitZone() {
        isInsideZone = false
        currentSpeedLimit = 0
        lastSpeedKmh = 0.0
        _isInZone.value = false
        _speedLimit.value = 0
        stopAlertLoop()
    }

    private fun startAlertLoop(speedKmh: Double) {
        stopAlertLoop()
        val intervalSeconds = getAlertInterval(speedKmh, currentSpeedLimit)
        playBeep()
        alertTimer = Timer()
        alertTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                playBeep()
            }
        }, intervalSeconds * 1000L, intervalSeconds * 1000L)
    }

    private fun stopAlertLoop() {
        alertTimer?.cancel()
        alertTimer = null
    }

    private fun playBeep() {
        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            }

            mediaPlayer?.let {
                if (it.isPlaying) it.seekTo(0)
                else it.start()
            }
        } catch (e: Exception) {
            android.util.Log.e("RadarAlertService", "Greška pri beep-u: ${e.message}")
        }
    }

    fun stopAlerts() {
        if (isInsideZone) exitZone()
    }

    fun dispose() {
        stopAlertLoop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}