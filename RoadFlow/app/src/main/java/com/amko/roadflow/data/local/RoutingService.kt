package com.amko.roadflow.data.local

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val coordinates: List<Pair<Double, Double>>
)

class RoutingService {
    private val client = OkHttpClient()
    private val baseUrl = Secrets.OSRM_URL

    suspend fun getRoutes(startLat: Double, startLng: Double, endLat: Double, endLng: Double): List<RouteResult> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/$startLng,$startLat;$endLng,$endLat?geometries=geojson&overview=full&alternatives=3"
                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string() ?: return@withContext emptyList()

                    Log.d("OSRM_DEBUG", "----------------- RAW OSRM JSON START -----------------")
                    Log.d("OSRM_DEBUG", body)
                    Log.d("OSRM_DEBUG", "----------------- RAW OSRM JSON END -------------------")

                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes")
                    if (routes == null || routes.length() == 0) return@withContext emptyList()

                    val results = mutableListOf<RouteResult>()

                    for (r in 0 until routes.length()) {
                        val route = routes.getJSONObject(r)
                        val distance = route.optDouble("distance", 0.0)
                        val duration = route.optDouble("duration", 0.0)

                        val geometry = route.getJSONObject("geometry")
                        val coordsArray = geometry.getJSONArray("coordinates")

                        val coordinates = mutableListOf<Pair<Double, Double>>()
                        for (i in 0 until coordsArray.length()) {
                            val coord = coordsArray.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            coordinates.add(Pair(lat, lon))
                        }

                        Log.d("OSRM_DEBUG", "Ruta $r -> Distanca: $distance m, Trajanje: $duration s, Ukupno koordinata: ${coordinates.size}")

                        results.add(RouteResult(distance, duration, coordinates))
                    }

                    results
                }
            } catch (e: Exception) {
                Log.e("OSRM_DEBUG", "Greska pri dohvacanju rute", e)
                emptyList()
            }
        }
    }

    suspend fun getRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double): RouteResult? {
        return getRoutes(startLat, startLng, endLat, endLng).firstOrNull()
    }
}