package com.amko.roadflow.data.local

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime


object TimeProvider {

    private const val TAG = "TimeProvider"

    private val TIME_API_URLS = listOf(
        "https://worldtimeapi.org/api/timezone/Europe/Sarajevo",
        "https://timeapi.io/api/time/current/zone?timeZone=Europe/Sarajevo"
    )

    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 1500L

    private val zoneId: ZoneId = ZoneId.of("Europe/Sarajevo")

    @Volatile
    private var offsetMillis: Long = 0L

    @Volatile
    private var isSynced: Boolean = false
    private val firstSyncCompleted = CompletableDeferred<Unit>()

    suspend fun awaitFirstSync() {
        firstSyncCompleted.await()
    }

    suspend fun sync() {
        withContext(Dispatchers.IO) {
            var success = false
            var lastError: Exception? = null

            for (attempt in 1..MAX_RETRIES) {
                for (url in TIME_API_URLS) {
                    try {
                        val serverEpochMillis = fetchServerEpochMillis(url)
                        val localEpochMillis = System.currentTimeMillis()
                        offsetMillis = serverEpochMillis - localEpochMillis
                        isSynced = true
                        success = true
                        android.util.Log.d(
                            TAG,
                            "Synced via $url (attempt $attempt), offsetMillis=$offsetMillis"
                        )
                        break
                    } catch (e: Exception) {
                        lastError = e
                        android.util.Log.w(TAG, "Sync failed via $url (attempt $attempt): ${e.message}")
                    }
                }
                if (success) break
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
            }

            if (!success) {
                isSynced = false
                android.util.Log.e(
                    TAG,
                    "All time sync attempts failed, falling back to raw system clock",
                    lastError
                )
            }

            if (!firstSyncCompleted.isCompleted) {
                firstSyncCompleted.complete(Unit)
            }
        }
    }

    fun isTimeSynced(): Boolean = isSynced

    fun instant(): Instant = Instant.ofEpochMilli(System.currentTimeMillis() + offsetMillis)

    fun now(): LocalDateTime = ZonedDateTime.ofInstant(instant(), zoneId).toLocalDateTime()

    fun nowDate(): LocalDate = now().toLocalDate()

    fun nowTime(): LocalTime = now().toLocalTime()

    private fun fetchServerEpochMillis(url: String): Long {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Time API ($url) returned HTTP $responseCode")
            }

            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(body)

            return when {
                json.has("unixtime") -> json.getLong("unixtime") * 1000L
                json.has("dateTime") -> {
                    val dateTimeStr = json.getString("dateTime")
                    val ldt = LocalDateTime.parse(dateTimeStr.substring(0, 19))
                    ldt.atZone(zoneId).toInstant().toEpochMilli()
                }
                else -> throw IllegalStateException("Unrecognized time API response shape from $url")
            }
        } finally {
            connection.disconnect()
        }
    }
}