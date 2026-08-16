package com.amko.roadflow.data.local

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI

data class RenderedPos(
    val lat: Double,
    val lng: Double,
    val bearing: Float
)

class ActiveTrackingAnimator {
    private val _renderedPos = MutableStateFlow(RenderedPos(0.0, 0.0, 0f))
    val renderedPos: StateFlow<RenderedPos> = _renderedPos.asStateFlow()

    private var targetLat = 0.0
    private var targetLng = 0.0
    private var targetBearing = 0f
    private var currentSpeedMs = 0f
    private var isInitialized = false

    private var renderedLat = 0.0
    private var renderedLng = 0.0
    private var renderedBearing = 0f

    fun updateFix(lat: Double, lng: Double, bearing: Float, speedKmh: Float, forceSnap: Boolean = false) {
        val speedMs = speedKmh / 3.6f
        if (!isInitialized || forceSnap) {
            targetLat = lat
            targetLng = lng
            targetBearing = bearing
            renderedLat = lat
            renderedLng = lng
            renderedBearing = bearing
            _renderedPos.value = RenderedPos(lat, lng, bearing)
            isInitialized = true
        } else {
            targetLat = lat
            targetLng = lng
            targetBearing = bearing
        }
        currentSpeedMs = speedMs
    }

    suspend fun start() {
        var lastFrameNanos = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameNanos
                    return@withFrameNanos
                }
                if (!isInitialized) {
                    lastFrameNanos = frameNanos
                    return@withFrameNanos
                }
                val dtSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000.0
                lastFrameNanos = frameNanos

                if (currentSpeedMs > 0.5f) {
                    val distanceMeters = currentSpeedMs * dtSeconds
                    val bearingRad = targetBearing * PI / 180.0
                    val latMeters = cos(bearingRad) * distanceMeters
                    val lngMeters = sin(bearingRad) * distanceMeters
                    val mPerLat = 111320.0
                    val mPerLng = 111320.0 * cos(targetLat * PI / 180.0)
                    targetLat += latMeters / mPerLat
                    targetLng += lngMeters / mPerLng
                }

                val catchUpFactorPos = (1.0 - exp(-dtSeconds * 4.0))
                renderedLat += (targetLat - renderedLat) * catchUpFactorPos
                renderedLng += (targetLng - renderedLng) * catchUpFactorPos

                val catchUpFactorBearing = (1.0 - exp(-dtSeconds * 5.0)).toFloat()
                var diff = (targetBearing - renderedBearing) % 360f
                if (diff > 180f) diff -= 360f
                if (diff < -180f) diff += 360f
                renderedBearing = (renderedBearing + diff * catchUpFactorBearing + 360f) % 360f

                _renderedPos.value = RenderedPos(renderedLat, renderedLng, renderedBearing)
            }
        }
    }
}