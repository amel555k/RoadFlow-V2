package com.amko.roadflow.data.local

import android.content.Context
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
    private val prefs = context.getSharedPreferences("roadflow_prefs", Context.MODE_PRIVATE)

    private fun currentFetchSlot(): String {
        val date = TimeProvider.effectiveRadarDate()
        val hour = TimeProvider.now().hour
        val window = if (hour < 18) 1 else 2
        return "$date-$window"
    }

    suspend fun loadCoordinatesAsync(forceRefresh: Boolean = false): List<RadarCoordinate> = withContext(Dispatchers.IO) {
        val lastFetchedSlot = prefs.getString("coords_last_fetch_slot", null)
        val currentSlot = currentFetchSlot()

        if (!forceRefresh && lastFetchedSlot == currentSlot && filePath.exists()) {
            val cached = readFromDiskAsync()
            if (cached.isNotEmpty()) {
                return@withContext cached
            }
        }

        val fetched = fetchAndCacheAsync()
        if (fetched.isNotEmpty()) {
            prefs.edit().putString("coords_last_fetch_slot", currentSlot).apply()
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
        val fetched = fetchAndCacheAsync()
        if (fetched.isNotEmpty()) {
            prefs.edit().putString("coords_last_fetch_slot", currentFetchSlot()).apply()
        }
        fetched
    }

    private suspend fun fetchAndCacheAsync(): List<RadarCoordinate> = withContext(Dispatchers.IO) {
        try {
            val mobilni = firebaseService.getMobilniCoordinatesAsync()
            val stacionirani = firebaseService.getStacionarniCoordinatesAsync()
            val combined = mobilni + stacionirani

            if (combined.isNotEmpty()) {
                saveToDiskAsync(combined)
            }

            combined
        } catch (e: Exception) {
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
                        startTime = if (obj.isNull("startTime")) null else obj.optString("startTime"),
                        endTime = if (obj.isNull("endTime")) null else obj.optString("endTime"),
                        stacionaran = obj.optBoolean("stacionaran", false),
                        city = if (obj.isNull("city")) null else obj.optString("city")
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveToDiskAsync(coordinates: List<RadarCoordinate>) = withContext(Dispatchers.IO) {
        try {
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
        } catch (e: Exception) {
        }
    }
}