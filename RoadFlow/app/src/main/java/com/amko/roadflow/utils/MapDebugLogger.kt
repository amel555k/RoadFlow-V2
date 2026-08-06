package com.amko.roadflow.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object MapDebugLogger {

    private val logBuffer = ConcurrentLinkedQueue<String>()
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        log("SYSTEM", "=== Logger init === device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} manufacturer=${Build.MANUFACTURER}")

        if (previousHandler == null) {
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                log("FATAL", "UncaughtException on ${thread.name}: ${throwable.stackTraceToString()}")
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun log(tag: String, message: String) {
        val line = "${sdf.format(Date())} [$tag] $message"
        android.util.Log.d("MapDebugLogger", line)
        logBuffer.add(line)
    }

    fun logMarkerAdded(markerType: String, id: String, lat: Double, lng: Double) {
        log("MARKER_LIFECYCLE", "ADDED | Type: $markerType | ID: $id | Lat: $lat | Lng: $lng")
    }

    fun logMarkerRemoved(markerType: String, id: String, reason: String) {
        log("MARKER_LIFECYCLE", "REMOVED | Type: $markerType | ID: $id | Reason: $reason")
    }

    fun logMarkerUpdated(markerType: String, id: String, updateType: String, details: String) {
        log("MARKER_LIFECYCLE", "UPDATED | Type: $markerType | ID: $id | Action: $updateType | Details: $details")
    }

    fun logMarkerError(markerType: String, operation: String, error: String) {
        log("MARKER_LIFECYCLE", "ERROR | Type: $markerType | Op: $operation | Details: $error")
    }

    fun logException(tag: String, message: String, throwable: Throwable) {
        log(tag, "$message | EXCEPTION: ${throwable.javaClass.simpleName}: ${throwable.message} | STACK: ${throwable.stackTraceToString().take(2000)}")
    }

    fun logMutex(tag: String, message: String) {
        log("MUTEX", "[$tag] $message")
    }

    fun logSymbolManagerLifecycle(generation: Int, reason: String) {
        log("SYMBOL_MANAGER", "REGENERATED | Generation: $generation | Reason: $reason")
    }

    fun saveToDownloads(context: Context): String? {
        if (logBuffer.isEmpty()) return "Prazan log - nema podataka za spašavanje"

        return try {
            val fileName = "roadflow_export_${System.currentTimeMillis()}.txt"
            val logsText = logBuffer.joinToString("\n")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(logsText.toByteArray())
                    }
                    "Sačuvano u Downloads/$fileName"
                } else null
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadsDir, fileName)
                targetFile.writeText(logsText)
                "Sačuvano u Downloads/$fileName"
            }
        } catch (e: Exception) {
            android.util.Log.e("MapDebugLogger", "Export failed: ${e.message}")
            null
        }
    }

    fun clearLogs() {
        logBuffer.clear()
    }
}