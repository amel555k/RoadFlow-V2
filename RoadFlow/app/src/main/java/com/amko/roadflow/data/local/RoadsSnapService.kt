package com.amko.roadflow.data.local

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class RoadsSnapService {

    private val client = OkHttpClient()

    suspend fun snapToRoad(location: Location): Location = withContext(Dispatchers.IO) {
        try {
            val path = "${location.latitude},${location.longitude}"
            val url = "https://roads.googleapis.com/v1/nearestRoads" +
                    "?points=$path&key=${Secrets.Z_M_API_KEY}"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                return@withContext location
            }

            val json = JSONObject(body)
            val points = json.optJSONArray("snappedPoints")
            if (points == null || points.length() == 0) {
                return@withContext location
            }

            val snapped = points.getJSONObject(0).getJSONObject("location")
            val snappedLat = snapped.getDouble("latitude")
            val snappedLng = snapped.getDouble("longitude")

            val result = Location(location)
            result.latitude = snappedLat
            result.longitude = snappedLng
            result
        } catch (e: Exception) {
            android.util.Log.e("RoadsSnapService", "Greška pri snap-u: ${e.message}")
            location
        }
    }
}