package com.amko.roadflow.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.Location
import android.os.Looper
import android.view.Display
import android.view.Surface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationTrackingService(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val displayManager: DisplayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _heading = MutableStateFlow(0.0)
    val heading: StateFlow<Double> = _heading.asStateFlow()

    private val _isActiveTracking = MutableStateFlow(false)
    val isActiveTracking: StateFlow<Boolean> = _isActiveTracking.asStateFlow()

    private val _speedKmh = MutableStateFlow<Float?>(null)
    val speedKmh: StateFlow<Float?> = _speedKmh.asStateFlow()

    fun setInitialLocation(location: Location) {
        _location.value = location
    }

    private var passiveCallback: LocationCallback? = null
    private var activeCallback: LocationCallback? = null
    private var sensorListener: SensorEventListener? = null
    private var speedWatchdogJob: Job? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val movementThresholdMeters = 1.0f
    private val minSpeedForBearingKmh = 5.0f
    private var lastBearing = 0.0
    private var lastCompassBearing = 0.0

    private var lastFixLat: Double? = null
    private var lastFixLng: Double? = null
    private var lastFixElapsedRealtimeMs: Long = 0L
    private val speedStaleTimeoutMs = 2500L
    private val speedZeroDistanceMeters = 1.5f
    private var activeFixCount = 0
    private val minFixesForReliableSpeed = 3
    private val maxAcceptableAccuracyMeters = 25f

    @SuppressLint("MissingPermission")
    fun startPassiveTracking() {
        stopPassiveTracking()

        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(3000L)
            .setMinUpdateDistanceMeters(movementThresholdMeters)
            .build()

        passiveCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val rawSpeedMs = if (loc.hasSpeed()) loc.speed else 0f
                    val speedKmh = rawSpeedMs * 3.6f
                    _speedKmh.value = speedKmh
                    updateBearing(loc, speedKmh)
                    _location.value = loc
                }
            }
        }
        fusedClient.requestLocationUpdates(request, passiveCallback!!, Looper.getMainLooper())

        if (!_isActiveTracking.value) {
            startCompass()
        }
    }

    fun stopPassiveTracking() {
        passiveCallback?.let { fusedClient.removeLocationUpdates(it) }
        passiveCallback = null
    }

    @SuppressLint("MissingPermission")
    fun startActiveTracking() {
        stopActiveTracking()
        lastBearing = lastCompassBearing
        stopCompass()
        _isActiveTracking.value = true
        lastFixLat = null
        lastFixLng = null
        lastFixElapsedRealtimeMs = 0L
        activeFixCount = 0
        _speedKmh.value = null

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        activeCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val nowElapsed = android.os.SystemClock.elapsedRealtime()

                    val distanceFromLastFix = if (lastFixLat != null && lastFixLng != null) {
                        val prev = Location("prev").apply {
                            latitude = lastFixLat!!
                            longitude = lastFixLng!!
                        }
                        prev.distanceTo(loc)
                    } else null

                    val rawSpeedMs = if (loc.hasSpeed()) loc.speed else 0f
                    var speedKmh = rawSpeedMs * 3.6f

                    val accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 20f
                    val movementThreshold = maxOf(speedZeroDistanceMeters, accuracyMeters * 0.5f)
                    val fixIsReliable = accuracyMeters <= maxAcceptableAccuracyMeters

                    if (distanceFromLastFix != null && distanceFromLastFix < movementThreshold && speedKmh < 3f) {
                        speedKmh = 0f
                    }

                    activeFixCount++

                    _speedKmh.value = when {
                        !fixIsReliable -> _speedKmh.value
                        activeFixCount < minFixesForReliableSpeed -> null
                        else -> speedKmh
                    }

                    lastFixLat = loc.latitude
                    lastFixLng = loc.longitude
                    lastFixElapsedRealtimeMs = nowElapsed

                    updateBearing(loc, speedKmh)

                    _location.value = loc
                }
            }
        }

        fusedClient.requestLocationUpdates(request, activeCallback!!, Looper.getMainLooper())

        speedWatchdogJob?.cancel()
        speedWatchdogJob = serviceScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(500)
                if (!_isActiveTracking.value) continue
                val last = lastFixElapsedRealtimeMs
                if (last == 0L) continue
                val staleFor = android.os.SystemClock.elapsedRealtime() - last
                if (staleFor > speedStaleTimeoutMs && activeFixCount >= minFixesForReliableSpeed && _speedKmh.value != 0f) {
                    _speedKmh.value = 0f
                }
            }
        }
    }

    private fun updateBearing(loc: Location, speedKmh: Float) {
        val targetBearing = if (loc.hasBearing() && speedKmh >= minSpeedForBearingKmh) {
            loc.bearing.toDouble()
        } else {
            lastBearing
        }

        val smoothed = smoothBearing(lastBearing, targetBearing)
        lastBearing = smoothed
        _heading.value = smoothed
    }

    private fun smoothBearing(current: Double, target: Double, factor: Double = 0.6): Double {
        var diff = target - current
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        val result = current + diff * factor
        return (result + 360) % 360
    }

    fun stopActiveTracking() {
        _isActiveTracking.value = false
        activeCallback?.let { fusedClient.removeLocationUpdates(it) }
        activeCallback = null
        speedWatchdogJob?.cancel()
        speedWatchdogJob = null
        lastFixLat = null
        lastFixLng = null
        lastFixElapsedRealtimeMs = 0L
        startCompass()
    }

    fun startCompass() {
        if (sensorListener != null) return

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometer == null || magnetometer == null) return

        sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        val alpha = 0.8f
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic[0] = event.values[0]
                        geomagnetic[1] = event.values[1]
                        geomagnetic[2] = event.values[2]
                    }
                }

                val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                if (success) {
                    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    val displayRotation = display?.rotation ?: Surface.ROTATION_0

                    when (displayRotation) {
                        Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix)
                        Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remappedMatrix)
                        Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remappedMatrix)
                        else -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, remappedMatrix)
                    }

                    SensorManager.getOrientation(remappedMatrix, orientation)
                    val azimuth = Math.toDegrees(orientation[0].toDouble())
                    val newCompassBearing = (azimuth + 360) % 360

                    lastCompassBearing = newCompassBearing

                    val gpsIsDriving = (_speedKmh.value ?: 0f) >= minSpeedForBearingKmh
                    if (!gpsIsDriving) {
                        var diff = newCompassBearing - _heading.value

                        while (diff > 180) diff -= 360
                        while (diff < -180) diff += 360

                        if (kotlin.math.abs(diff) > 3.0) {
                            _heading.value = newCompassBearing
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopCompass() {
        sensorListener?.let { sensorManager.unregisterListener(it) }
        sensorListener = null
    }

    private val maxCachedLocationAgeMillis = 45_000L

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    cont.resume(null)
                    return@addOnSuccessListener
                }
                val ageMillis = android.os.SystemClock.elapsedRealtime() - (loc.elapsedRealtimeNanos / 1_000_000L)
                if (ageMillis > maxCachedLocationAgeMillis) {
                    cont.resume(null)
                } else {
                    cont.resume(loc)
                }
            }
            .addOnFailureListener { cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMaxUpdates(1)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedClient.removeLocationUpdates(this)
                cont.resume(result.lastLocation)
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        cont.invokeOnCancellation {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    fun dispose() {
        stopPassiveTracking()
        stopActiveTracking()
        stopCompass()
        serviceScope.cancel()
    }
}