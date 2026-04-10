    package com.amko.roadflow.data.local

    import android.annotation.SuppressLint
    import android.content.Context
    import android.hardware.Sensor
    import android.hardware.SensorEvent
    import android.hardware.SensorEventListener
    import android.hardware.SensorManager
    import android.location.Location
    import android.os.Looper
    import com.google.android.gms.location.FusedLocationProviderClient
    import com.google.android.gms.location.LocationCallback
    import com.google.android.gms.location.LocationRequest
    import com.google.android.gms.location.LocationResult
    import com.google.android.gms.location.LocationServices
    import com.google.android.gms.location.Priority
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.suspendCancellableCoroutine
    import kotlin.coroutines.resume

    class LocationTrackingService(private val context: Context) {

        private val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        private val sensorManager: SensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        private val _location = MutableStateFlow<Location?>(null)
        val location: StateFlow<Location?> = _location.asStateFlow()

        private val _heading = MutableStateFlow(0.0)
        val heading: StateFlow<Double> = _heading.asStateFlow()

        private val _isActiveTracking = MutableStateFlow(false)
        val isActiveTracking: StateFlow<Boolean> = _isActiveTracking.asStateFlow()
        private val _speedKmh = MutableStateFlow(0f)
        val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()
        fun setInitialLocation(location: Location) {
            _location.value = location
        }

        private var passiveCallback: LocationCallback? = null
        private var activeCallback: LocationCallback? = null
        private var sensorListener: SensorEventListener? = null

        private val gravity = FloatArray(3)
        private val geomagnetic = FloatArray(3)
        private val rotationMatrix = FloatArray(9)
        private val orientation = FloatArray(3)

        private val movementThresholdMeters = 1.0f

        private var deviceOrientation = android.content.res.Configuration.ORIENTATION_PORTRAIT
        @SuppressLint("MissingPermission")
        fun startPassiveTracking() {
            stopPassiveTracking()

            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            android.util.Log.d("LocationService", "startPassiveTracking - hasPermission: $hasPermission")

            if (!hasPermission) {
                android.util.Log.d("LocationService", "NEMA DOZVOLE - tracking se ne pokrece")
                return
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(movementThresholdMeters)
                .build()

            passiveCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    android.util.Log.d("LocationService", "Lokacija primljena: ${result.lastLocation?.latitude}, ${result.lastLocation?.longitude}")
                    result.lastLocation?.let { loc ->
                        _location.value = loc
                    }
                }
            }

            fusedClient.requestLocationUpdates(request, passiveCallback!!, Looper.getMainLooper())
            android.util.Log.d("LocationService", "requestLocationUpdates pozvan uspjesno")
            startCompass()
        }

        fun stopPassiveTracking() {
            passiveCallback?.let { fusedClient.removeLocationUpdates(it) }
            passiveCallback = null
        }

        @SuppressLint("MissingPermission")
        fun startActiveTracking() {
            stopActiveTracking()
            _isActiveTracking.value = true

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(movementThresholdMeters)
                .build()

            activeCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        _location.value = loc

                        val speedMs = if (loc.hasSpeed()) loc.speed else 0f
                        _speedKmh.value = speedMs * 3.6f
                    }
                }
            }

            fusedClient.requestLocationUpdates(request, activeCallback!!, Looper.getMainLooper())
            startCompass()
        }

        fun stopActiveTracking() {
            _isActiveTracking.value = false
            activeCallback?.let { fusedClient.removeLocationUpdates(it) }
            activeCallback = null
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
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        deviceOrientation = context.resources.configuration.orientation
                        val azimuth = Math.toDegrees(orientation[0].toDouble())
                        _heading.value = (azimuth + 360) % 360
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

        @SuppressLint("MissingPermission")
        suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
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
        }
    }