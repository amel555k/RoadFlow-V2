package com.amko.roadflow.data.local

import android.content.Context
import android.util.Log
import com.amko.roadflow.domain.model.RadarCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CoordinateRepository(
    private val context: Context,
    private val firebaseService: FirebaseService
) {
    private val filePath = File(context.filesDir, "coordinates.json")

    suspend fun loadCoordinatesAsync(forceRefresh: Boolean = false): List<RadarCoordinate> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val fetched = fetchAndCacheAsync()
        if (fetched.isNotEmpty()) {
            return@withContext fetched
        }

        if (filePath.exists()) {
            val cached = readFromDiskAsync()
            if (cached.isNotEmpty()) {
                return@withContext cached
            }
        }

        emptyList()
    }

    suspend fun refreshCoordinatesAsync(): List<RadarCoordinate> = withContext(Dispatchers.IO) {
        fetchAndCacheAsync()
    }

    private suspend fun fetchAndCacheAsync(): List<RadarCoordinate> = withContext(Dispatchers.IO) {
        try {
            val mobilni = firebaseService.getMobilniCoordinatesAsync()
            val stacionirani = firebaseService.getStacionarniCoordinatesAsync()
            val combined = mobilni + stacionirani

            combined
                .filter {
                    it.mainName.contains("Brank", true) ||
                            it.mainName.contains("Duh", true) ||
                            it.mainName.contains("Kaonik", true)
                }
                .forEach {
                    Log.d(
                        "COORD_DEBUG",
                        "mainName='${it.mainName}' | city='${it.city}' | lat=${it.latitude} | lon=${it.longitude}"
                    )
                }

            if (combined.isNotEmpty()) {
                saveToDiskAsync(combined)
            }

            combined
        } catch (e: Exception) {
            android.util.Log.d("WidgetDebug", "CoordinateRepository: fetchAndCacheAsync EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private suspend fun readFromDiskAsync(): List<RadarCoordinate> = withContext(Dispatchers.IO) {
        try {
            val text = filePath.readText(Charsets.UTF_8)
            if (text.isBlank()) return@withContext emptyList()

            val jsonArray = JSONArray(text)
            val result = mutableListOf<RadarCoordinate>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    RadarCoordinate(
                        mainName = obj.optString("mainName", ""),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        speedLimit = obj.optInt("speedLimit", 0),
                        startTime = if (obj.isNull("startTime")) null else obj.optString("startTime", null),
                        endTime = if (obj.isNull("endTime")) null else obj.optString("endTime", null),
                        stacionaran = obj.optBoolean("stacionaran", false),
                        city = if (obj.isNull("city")) null else obj.optString("city", null)
                    )
                )
            }
            result
        } catch (e: Exception) {
            android.util.Log.d("WidgetDebug", "CoordinateRepository: readFromDiskAsync EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private suspend fun saveToDiskAsync(coordinates: List<RadarCoordinate>) = withContext(Dispatchers.IO) {
        try {
            val startSave = System.currentTimeMillis()
            val jsonArray = JSONArray()
            coordinates.forEach { coord ->
                val obj = JSONObject()
                obj.put("mainName", coord.mainName)
                obj.put("latitude", coord.latitude)
                obj.put("longitude", coord.longitude)
                obj.put("speedLimit", coord.speedLimit)
                obj.put("startTime", coord.startTime)
                obj.put("endTime", coord.endTime)
                obj.put("stacionaran", coord.stacionaran)
                obj.put("city", coord.city)
                jsonArray.put(obj)
            }
            filePath.writeText(jsonArray.toString(), Charsets.UTF_8)
            Log.d("BrzinaFetcha", "Podaci uspjesno sacuvani u lokalni JSON. Putanja: ${filePath.absolutePath}, trajanje cuvanja: ${System.currentTimeMillis() - startSave} ms")
        } catch (e: Exception) {
            android.util.Log.d("WidgetDebug", "CoordinateRepository: saveToDiskAsync EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}