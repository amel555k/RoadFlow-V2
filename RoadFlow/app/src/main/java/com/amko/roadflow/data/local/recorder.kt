package com.amko.roadflow.data.local

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TrackingRecorder {

    private const val FILE_ROTATION_INTERVAL_MS = 60_000L

    private var logDir: File? = null
    private var currentFile: File? = null
    private var writer: FileWriter? = null
    private var currentFileOpenedAtMs: Long = 0L
    private var fileIndex: Int = 0

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val channel = Channel<String>(capacity = Channel.UNLIMITED)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var isStarted = false
    private var sessionStartElapsedNanos = 0L
    private var sessionStartWallTimeMs = 0L
    private lateinit var sessionLabel: String

    fun start(context: Context) {
        if (isStarted) return
        isStarted = true
        sessionStartElapsedNanos = System.nanoTime()
        sessionStartWallTimeMs = System.currentTimeMillis()
        sessionLabel = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        fileIndex = 0

        val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        logDir = downloadsFolder.takeIf { it.exists() || it.mkdirs() } ?: context.getExternalFilesDir(null) ?: context.filesDir

        openNewFile()

        scope.launch {
            for (line in channel) {
                try {
                    rotateFileIfNeeded()
                    writer?.appendLine(line)
                    writer?.flush()
                } catch (e: Exception) {
                }
            }
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        try {
            writer?.appendLine("=== RoadFlow Tracking Recorder session end (part $fileIndex) ===")
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
        }
        writer = null
        currentFile = null
    }

    fun getLogFilePath(): String? = currentFile?.absolutePath

    fun getAllLogFilePaths(): List<String> {
        val dir = logDir ?: return emptyList()
        val prefix = "roadflow_track_${sessionLabel}_part"
        return dir.listFiles { f -> f.name.startsWith(prefix) }
            ?.sortedBy { it.name }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    private fun openNewFile() {
        try {
            writer?.appendLine("=== RoadFlow Tracking Recorder session end (part $fileIndex) ===")
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
        }

        fileIndex += 1
        val dir = logDir ?: return
        val fileName = "roadflow_track_${sessionLabel}_part${fileIndex.toString().padStart(3, '0')}.txt"
        val file = File(dir, fileName)
        currentFile = file
        currentFileOpenedAtMs = System.currentTimeMillis()

        try {
            writer = FileWriter(file, true)
            writer?.appendLine("=== RoadFlow Tracking Recorder session start (part $fileIndex) ===")
            writer?.appendLine("File: ${file.absolutePath}")
            writer?.appendLine("SessionStartWallTimeMs: $sessionStartWallTimeMs")
            writer?.flush()
        } catch (e: Exception) {
            writer = null
        }
    }

    private fun rotateFileIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - currentFileOpenedAtMs >= FILE_ROTATION_INTERVAL_MS) {
            openNewFile()
        }
    }

    private fun ts(): String = timestampFormat.format(Date())

    private fun elapsedMs(): Long = (System.nanoTime() - sessionStartElapsedNanos) / 1_000_000L

    private fun enqueue(line: String) {
        if (!isStarted) return
        channel.trySend("[${ts()}][+${elapsedMs()}ms] $line")
    }

    fun logGpsFix(
        lat: Double,
        lng: Double,
        accuracyMeters: Float?,
        hasBearing: Boolean,
        rawBearing: Float?,
        hasSpeed: Boolean,
        rawSpeedMs: Float?,
        computedSpeedKmh: Float,
        elapsedRealtimeNanos: Long,
        msSinceLastFix: Long
    ) {
        enqueue(
            "GPS_FIX lat=$lat lng=$lng accuracy=${accuracyMeters ?: "null"}m " +
                    "hasBearing=$hasBearing rawBearing=${rawBearing ?: "null"} " +
                    "hasSpeed=$hasSpeed rawSpeedMs=${rawSpeedMs ?: "null"} computedSpeedKmh=$computedSpeedKmh " +
                    "elapsedRealtimeNanos=$elapsedRealtimeNanos msSinceLastFix=$msSinceLastFix"
        )
    }

    fun logBearingUpdate(
        targetBearing: Double,
        smoothedBearing: Double,
        speedKmh: Float,
        minSpeedThresholdKmh: Float,
        usedGpsBearing: Boolean
    ) {
        enqueue(
            "BEARING_UPDATE target=$targetBearing smoothed=$smoothedBearing speedKmh=$speedKmh " +
                    "minSpeedThreshold=$minSpeedThresholdKmh usedGpsBearing=$usedGpsBearing"
        )
    }

    fun logCompassUpdate(compassBearing: Double, currentHeading: Double, diff: Double, accepted: Boolean) {
        enqueue(
            "COMPASS_UPDATE compassBearing=$compassBearing currentHeading=$currentHeading diff=$diff accepted=$accepted"
        )
    }

    fun logSnapAttempt(
        userLat: Double,
        userLng: Double,
        userBearing: Double,
        routePointsCount: Int,
        result: String,
        bestDistanceMeters: Double?,
        bestSegmentBearing: Double?,
        angleDiffDegrees: Double?,
        snappedLat: Double?,
        snappedLng: Double?
    ) {
        enqueue(
            "SNAP_ATTEMPT userLat=$userLat userLng=$userLng userBearing=$userBearing " +
                    "routePoints=$routePointsCount result=$result " +
                    "bestDistanceMeters=${bestDistanceMeters ?: "null"} " +
                    "bestSegmentBearing=${bestSegmentBearing ?: "null"} " +
                    "angleDiffDegrees=${angleDiffDegrees ?: "null"} " +
                    "snappedLat=${snappedLat ?: "null"} snappedLng=${snappedLng ?: "null"}"
        )
    }

    fun logAnimationFrame(
        renderedLat: Double,
        renderedLng: Double,
        renderedBearing: Float,
        targetLat: Double,
        targetLng: Double,
        targetBearing: Float,
        velocityLatPerSec: Double,
        velocityLngPerSec: Double,
        dtSeconds: Double,
        remainingLatMeters: Double,
        remainingLngMeters: Double,
        overshotLat: Boolean,
        overshotLng: Boolean
    ) {
        enqueue(
            "ANIM_FRAME rendered=($renderedLat,$renderedLng) bearing=$renderedBearing " +
                    "target=($targetLat,$targetLng) targetBearing=$targetBearing " +
                    "velocity=($velocityLatPerSec,$velocityLngPerSec)/s dt=${dtSeconds}s " +
                    "remaining=(${remainingLatMeters}m,${remainingLngMeters}m) " +
                    "overshotLat=$overshotLat overshotLng=$overshotLng"
        )
    }

    fun logAnimationStall(reasonNote: String, msSinceLastFix: Long, msSinceLastFrame: Long) {
        enqueue(
            "ANIM_STALL reason=$reasonNote msSinceLastFix=$msSinceLastFix msSinceLastFrame=$msSinceLastFrame"
        )
    }

    fun logAnimationRestart(
        reason: String,
        jumpDistanceMeters: Double,
        previousLat: Double?,
        previousLng: Double?,
        newLat: Double,
        newLng: Double
    ) {
        enqueue(
            "ANIM_RESTART reason=$reason jumpDistanceMeters=$jumpDistanceMeters " +
                    "previous=(${previousLat ?: "null"},${previousLng ?: "null"}) new=($newLat,$newLng)"
        )
    }

    fun logRouteLoaded(source: String, pointsCount: Int, firstLat: Double?, firstLng: Double?, lastLat: Double?, lastLng: Double?) {
        enqueue(
            "ROUTE_LOADED source=$source pointsCount=$pointsCount " +
                    "first=(${firstLat ?: "null"},${firstLng ?: "null"}) last=(${lastLat ?: "null"},${lastLng ?: "null"})"
        )
    }

    fun logRouteCleared() {
        enqueue("ROUTE_CLEARED")
    }

    fun logTrackingStateChange(isActiveTracking: Boolean, reason: String) {
        enqueue("TRACKING_STATE isActiveTracking=$isActiveTracking reason=$reason")
    }

    fun logGeoJsonPush(lat: Double, lng: Double, rotation: Float, forced: Boolean, throttled: Boolean) {
        enqueue("GEOJSON_PUSH lat=$lat lng=$lng rotation=$rotation forced=$forced throttled=$throttled")
    }

    fun logCameraUpdate(
        mode: String,
        targetLat: Double,
        targetLng: Double,
        zoom: Double,
        tilt: Double,
        bearing: Double
    ) {
        enqueue("CAMERA_UPDATE mode=$mode target=($targetLat,$targetLng) zoom=$zoom tilt=$tilt bearing=$bearing")
    }

    fun logGeneric(tag: String, message: String) {
        enqueue("$tag $message")
    }
}