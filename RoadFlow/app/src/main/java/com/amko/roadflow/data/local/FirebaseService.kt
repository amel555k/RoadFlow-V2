package com.amko.roadflow.data.local

import com.amko.roadflow.domain.model.FirebaseRadarItem
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FirebaseService {

    companion object {
        val FIREBASE_BASE_URL = "${Secrets.FIREBASE_BASE_URL}radari-sbk"
        val HISTORY_BASE_URL = "${Secrets.FIREBASE_BASE_URL}history"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var cachedToken: String? = null

    private suspend fun getAuthTokenAsync(): String? = withContext(Dispatchers.IO) {
        if (!cachedToken.isNullOrEmpty()) return@withContext cachedToken

        try {
            val authUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${Secrets.FIREBASE_API_KEY}"
            val body = "{\"returnSecureToken\":true}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(authUrl).post(body).build()
            val response = client.newCall(request).execute()

            android.util.Log.d("WidgetDebug", "FirebaseService: getAuthTokenAsync response code=${response.code}")

            if (response.isSuccessful) {
                val content = response.body?.string() ?: return@withContext null
                val json = JSONObject(content)
                cachedToken = json.getString("idToken")
                android.util.Log.d("WidgetDebug", "FirebaseService: token dobijen uspjesno, duzina=${cachedToken?.length}")
                return@withContext cachedToken
            } else {
                val errorBody = response.body?.string() ?: ""
                android.util.Log.d("WidgetDebug", "FirebaseService: getAuthToken NEUSPJESAN response code=${response.code}, body=$errorBody")
            }
        } catch (e: Exception) {
            android.util.Log.d("WidgetDebug", "FirebaseService: getAuthToken EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
        }

        null
    }

    private suspend fun getAuthenticatedUrl(path: String): String {
        val token = getAuthTokenAsync()
        val separator = if (path.contains("?")) "&" else "?"
        return "$path${separator}auth=$token"
    }

    suspend fun getFirebaseRadarsAsync(date: LocalDate): List<RadarData> = withContext(Dispatchers.IO) {
        val radars = mutableListOf<RadarData>()
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val url = getAuthenticatedUrl("$FIREBASE_BASE_URL/$dateStr.json")

        android.util.Log.d("WidgetDebug", "FirebaseService: getFirebaseRadarsAsync pozivam URL (bez tokena u logu za sigurnost)")

        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            android.util.Log.d("WidgetDebug", "FirebaseService: getFirebaseRadarsAsync response code=${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                android.util.Log.d("WidgetDebug", "FirebaseService: getFirebaseRadarsAsync NEUSPJESAN, code=${response.code}, body=$errorBody")
                return@withContext radars
            }

            val json = response.body?.string()
            if (json.isNullOrBlank() || json == "null") {
                android.util.Log.d("WidgetDebug", "FirebaseService: getFirebaseRadarsAsync JSON prazan ili null")
                return@withContext radars
            }

            val rootObj = JSONObject(json)

            rootObj.keys().forEach { cityName ->
                val configLoc = RadarConfig.locations.firstOrNull { it.name == cityName && it.fromFirebase }
                    ?: return@forEach

                val itemsArray = rootObj.getJSONArray(cityName)
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val item = FirebaseRadarItem(
                        time = itemObj.optString("Time", ""),
                        location = itemObj.optString("Location", "")
                    )

                    val locationPart = normalizeFirebaseLocation(item.location)

                    val radar = RadarData(
                        city = cityName,
                        time = item.time,
                        location = locationPart,
                        pageDate = date.atStartOfDay()
                    )

                    if (configLoc.mapEnabled) {
                        val coords = RadarConfig.findCoordinatesByName(locationPart, city = cityName)
                        if (coords.isNotEmpty()) {
                            val first = coords.first()
                            radars.add(
                                radar.copy(
                                    coordinate = first,
                                    latitude = first.latitude,
                                    longitude = first.longitude,
                                    speedLimit = first.speedLimit
                                )
                            )
                        } else {
                            radars.add(radar)
                        }
                    } else {
                        radars.add(radar)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("WidgetDebug", "FirebaseService: getFirebaseRadarsAsync EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
        }

        radars
    }

    suspend fun getHistoryRadarsAsync(date: LocalDate): List<RadarData> = withContext(Dispatchers.IO) {
        val radars = mutableListOf<RadarData>()
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val url = getAuthenticatedUrl("$HISTORY_BASE_URL/$dateStr.json")

        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext radars

            val json = response.body?.string()
            if (json.isNullOrBlank() || json == "null") return@withContext radars

            val rootObj = JSONObject(json)

            rootObj.keys().forEach { cityName ->
                val configLoc = RadarConfig.locations.firstOrNull { it.name == cityName }
                    ?: return@forEach

                val itemsArray = rootObj.getJSONArray(cityName)
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val item = FirebaseRadarItem(
                        time = itemObj.optString("Time", ""),
                        location = itemObj.optString("Location", "")
                    )

                    val locationPart = normalizeFirebaseLocation(item.location)

                    val radar = RadarData(
                        city = cityName,
                        time = item.time,
                        location = locationPart,
                        pageDate = date.atStartOfDay()
                    )

                    if (configLoc.mapEnabled) {
                        val coords = RadarConfig.findCoordinatesByName(locationPart, city = cityName)
                        if (coords.isNotEmpty()) {
                            val first = coords.first()
                            radars.add(
                                radar.copy(
                                    coordinate = first,
                                    latitude = first.latitude,
                                    longitude = first.longitude,
                                    speedLimit = first.speedLimit
                                )
                            )
                        } else {
                            radars.add(radar)
                        }
                    } else {
                        radars.add(radar)
                    }
                }
            }
        } catch (e: Exception) {
            println("[FirebaseService] History error: ${e.message}")
        }

        radars
    }
    private fun normalizeFirebaseLocation(raw: String): String {
        if (raw.isBlank()) return raw
        return raw.trim()
    }

    suspend fun saveRadarsToHistoryAsync(date: LocalDate, radars: List<RadarData>) = withContext(Dispatchers.IO) {
        try {
            if (radars.isEmpty()) return@withContext

            TimeProvider.sync()
            if (TimeProvider.isTimeSynced() && TimeProvider.nowDate() != date) return@withContext

            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val url = getAuthenticatedUrl("${Secrets.FIREBASE_BASE_URL}history/$dateStr.json")
            val existing = getHistoryRadarsAsync(date)

            val merged = (existing + radars).distinctBy { "${it.city}|${it.time}|${it.location}" }

            val groupedData = JSONObject()
            merged.groupBy { it.city }.forEach { (city, cityRadars) ->
                val jsonArray = org.json.JSONArray()
                cityRadars.forEach { radar ->
                    val radarObj = JSONObject()
                    radarObj.put("Time", radar.time)
                    radarObj.put("Location", radar.location)
                    jsonArray.put(radarObj)
                }
                groupedData.put(city, jsonArray)
            }

            val body = groupedData.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                println("[FirebaseService] Podaci uspješno merge-ovani u history za datum: $dateStr (ukupno: ${merged.size})")
            }
        } catch (e: Exception) {
            println("[FirebaseService] Greška pri slanju u history: ${e.message}")
        }
    }
}