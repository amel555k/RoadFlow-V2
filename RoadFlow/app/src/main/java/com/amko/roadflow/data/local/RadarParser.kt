package com.amko.roadflow.data.local

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.flowOn
import com.amko.roadflow.domain.model.Canton
class NoInternetWithCacheException(val cachedRadars: List<RadarData>) : Exception("Nema internet konekcije")

class RadarParser(
    private val context: Context,
    private val firebaseService: FirebaseService
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
        .build()
    private val filePath = File(context.filesDir, "lista.txt")
    private val baseUrl = Secrets.BASE_URL

    private val htmlCache = ConcurrentHashMap<Int, kotlinx.coroutines.Deferred<String>>()
    private val cacheMutex = Mutex()

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val TIME_PATTERN = Regex("""\d{1,2}:\d{2}(?:\s*sati)?\s*[–\-do]+\s*\d{1,2}:\d{2}(?:\s*sati)?""")
        private val SATI_REPLACE_REGEX = Regex("\\s*sati", RegexOption.IGNORE_CASE)
        private val SATI_REGEX = Regex("\\s*sati\\s*", RegexOption.IGNORE_CASE)
        private val DATE_PATTERN = Regex("""([0-9]{1,2})[.\s]+([0-9]{1,2})[.\s]+([0-9]{4})""")
    }

    suspend fun parseAllLocationsAsFlow(): kotlinx.coroutines.flow.Flow<List<RadarData>> =
        kotlinx.coroutines.flow.flow {
            val todayDate = LocalDate.now()

            if (filePath.exists()) {
                val lastModified = LocalDate.ofEpochDay(filePath.lastModified() / 86400000L)
                if (lastModified == todayDate) {
                    emit(parseFileContent(readFromFileAsync()))
                    return@flow
                }
            }

            if (!isInternetAvailable()) {
                if (filePath.exists()) {
                    val cached = parseFileContent(readFromFileAsync())
                    if (cached.isNotEmpty()) throw NoInternetWithCacheException(cached)
                }
                throw java.net.UnknownHostException("Nema internet konekcije")
            }

            cacheMutex.withLock { htmlCache.clear() }

            val accumulated = mutableListOf<RadarData>()

            coroutineScope {
                val firebaseData = firebaseService.getFirebaseRadarsAsync(todayDate)
                accumulated.addAll(firebaseData)
                emit(accumulated.sortedWith(compareByDescending<RadarData> { it.city }
                    .thenByDescending { it.pageDate ?: LocalDateTime.MIN }))
                val locationGroups = RadarConfig.locations.filter { !it.fromFirebase }

                for (location in locationGroups) {
                    val deferredList = location.possibleIds.map { id ->
                        async { parseSingleIdWithErrorHandlingAsync(location.name, id, location.mapEnabled) }
                    }

                    deferredList.awaitAll().forEach { radarsFromLink ->
                        radarsFromLink.forEach { radar ->
                            if ((radar.pageDate != null && radar.pageDate.toLocalDate() == todayDate) || radar.time == "INFO") {
                                accumulated.add(radar)
                            }
                        }
                    }

                    emit(accumulated
                        .filter { it.time != "INFO" }
                        .groupBy { Triple(it.city, it.time, it.location) }
                        .map { it.value.first() }
                        .sortedWith(compareByDescending<RadarData> { it.city }
                            .thenByDescending { it.pageDate ?: LocalDateTime.MIN }))
                }
            }

            val deduped = accumulated
                .filter { it.time != "INFO" }
                .groupBy { Triple(it.city, it.time, it.location) }
                .map { it.value.first() }
                .sortedWith(compareByDescending<RadarData> { it.city }
                    .thenByDescending { it.pageDate ?: LocalDateTime.MIN })

            val uniqueRadars = if (deduped.isEmpty()) {
                listOf(RadarData(
                    city = "STATUS SISTEMA", time = "INFO",
                    location = "Nisu pronađeni radari za današnji datum (${todayDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}).",
                    pageDate = LocalDateTime.now()
                ))
            } else deduped

            emit(uniqueRadars)

            val sb = StringBuilder()
            uniqueRadars.groupBy { it.city }.forEach { (city, radars) ->
                sb.appendLine("=== $city ===")
                radars.forEach { sb.appendLine("${it.time} - ${it.location}") }
                sb.appendLine()
            }
            saveToFileAsync(sb.toString())

            if (uniqueRadars.any { it.time != "INFO" }) {
                firebaseService.saveRadarsToHistoryAsync(todayDate, uniqueRadars)
            }
        }.flowOn(Dispatchers.IO)
    private suspend fun parseSingleIdWithErrorHandlingAsync(baseCityName: String, id: Int, mapEnabled: Boolean): List<RadarData> {
        return try {
            parseSingleIdAsync(baseCityName, id, mapEnabled)
        } catch (e: Exception) {
            println("[RadarParser] Greška pri parsiranju ID-a $id: ${e.message}")
            emptyList()
        }
    }

    private suspend fun parseSingleIdAsync(baseCityName: String, id: Int, mapEnabled: Boolean): List<RadarData> = withContext(Dispatchers.IO) {
        val radars = mutableListOf<RadarData>()
        val url = "$baseUrl$id"

        val deferredHtml = cacheMutex.withLock {
            htmlCache.getOrPut(id) {
                kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { it.body?.string() ?: "" }
                }
            }
        }
        val html = deferredHtml.await()

        val doc = Jsoup.parse(html)
        var fullText = doc.body().text()
        fullText = fullText.replace("\u00A0", " ")
        fullText = fullText.replace(WHITESPACE_REGEX, " ")

        if (id != 323 && id != 393 && !cityNameExistsInHtml(fullText, baseCityName)) {
            return@withContext emptyList()
        }

        val foundDate = extractDateFromHtml(doc, fullText)
        val cityName = baseCityName
        val timeMatches = TIME_PATTERN.findAll(fullText).toList()

        if (timeMatches.isNotEmpty()) {
            timeMatches.forEachIndexed { i, timeMatch ->
                val rawTime = timeMatch.value.trim()
                var cleanTime = rawTime.replace(SATI_REPLACE_REGEX, "")
                cleanTime = cleanTime.replace("–", " do ").replace("-", " do ")
                cleanTime = cleanTime.replace(WHITESPACE_REGEX, " ").trim()

                val startPos = timeMatch.range.last + 1
                val endPos = if (i < timeMatches.size - 1) timeMatches[i + 1].range.first else fullText.length

                var locationPart = fullText.substring(startPos, endPos).trim()
                locationPart = locationPart.trimStart('-', ':', ' ', ',', '.', '–', '—').trim()

                val googleIndex = locationPart.indexOf("PRIKAŽI NA GOOGLE MAPI", ignoreCase = true)
                if (googleIndex >= 0) locationPart = locationPart.substring(0, googleIndex)

                val zatvoriIndex = locationPart.indexOf("Zatvori", ignoreCase = true)
                if (zatvoriIndex >= 0) locationPart = locationPart.substring(0, zatvoriIndex)

                locationPart = locationPart.trim()

                if (locationPart.isNotBlank()) {
                    locationPart = preprocessBihamkLocation(locationPart)

                    if (mapEnabled) {
                        val coords = RadarConfig.findCoordinatesByName(locationPart)
                        if (coords.isNotEmpty()) {
                            coords.forEach { coordinate ->
                                radars.add(
                                    RadarData(
                                        city = cityName,
                                        time = cleanTime,
                                        location = locationPart,
                                        pageDate = foundDate,
                                        coordinate = coordinate,
                                        latitude = coordinate.latitude,
                                        longitude = coordinate.longitude,
                                        speedLimit = coordinate.speedLimit
                                    )
                                )
                            }
                        } else {
                            radars.add(
                                RadarData(
                                    city = cityName,
                                    time = cleanTime,
                                    location = locationPart,
                                    pageDate = foundDate
                                )
                            )
                        }
                    } else {
                        radars.add(
                            RadarData(
                                city = cityName,
                                time = cleanTime,
                                location = locationPart,
                                pageDate = foundDate
                            )
                        )
                    }
                }
            }
        } else {
            radars.add(
                RadarData(
                    city = cityName,
                    time = "INFO",
                    location = "Nema planiranih radara za ovaj ID.",
                    pageDate = foundDate ?: LocalDateTime.MIN
                )
            )
        }

        radars
    }

    private var cachedAllRadars: List<RadarData> = emptyList()

    suspend fun getRadarsForCanton(canton: Canton): List<RadarData> {
        if (cachedAllRadars.isEmpty()) {
            cachedAllRadars = parseFileContent(readFromFileAsync())
        }
        val citiesInCanton = RadarConfig.locations
            .filter { it.canton == canton }
            .map { it.name }
            .toHashSet()
        return cachedAllRadars.filter { citiesInCanton.contains(it.city) }
    }

    fun updateCache(radars: List<RadarData>) {
        cachedAllRadars = radars
    }
    private fun preprocessBihamkLocation(location: String): String {
        if (location.isBlank()) return location
        var result = location.replace(SATI_REGEX, " ")
        result = result.replace(WHITESPACE_REGEX, " ")
        return result.trim()
    }

    private fun extractDateFromHtml(doc: org.jsoup.nodes.Document, fullText: String): LocalDateTime? {
        val titleNode = doc.selectFirst("h1") ?: doc.selectFirst("h2") ?: doc.selectFirst("title")
        if (titleNode != null) {
            val titleText = titleNode.text()
            val dateFromTitle = extractDateFromText(titleText)
            if (dateFromTitle != null) return dateFromTitle
        }
        val matches = DATE_PATTERN.findAll(fullText).toList()
        if (matches.isNotEmpty()) {
            return tryParseDateTime(matches.last())
        }
        return null
    }

    private fun extractDateFromText(text: String): LocalDateTime? {
        val match = DATE_PATTERN.find(text) ?: return null
        return tryParseDateTime(match)
    }

    private fun tryParseDateTime(match: MatchResult): LocalDateTime? {
        return try {
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            LocalDateTime.of(year, month, day, 0, 0)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveToFileAsync(content: String) {
        withContext(Dispatchers.IO) {
            try {
                filePath.writeText(content, Charsets.UTF_8)
                filePath.setLastModified(System.currentTimeMillis())
                println("[RadarParser] Podaci sačuvani u ${filePath.absolutePath}")
            } catch (e: Exception) {
                println("[RadarParser] Greška pri čuvanju fajla: ${e.message}")
            }
        }
    }

    suspend fun readFromFileAsync(): String {
        return withContext(Dispatchers.IO) {
            try {
                if (filePath.exists()) filePath.readText(Charsets.UTF_8) else ""
            } catch (e: Exception) {
                ""
            }
        }
    }

    suspend fun getActiveRadarsAsync(): List<RadarData> {
        val fileContent = readFromFileAsync()
        if (fileContent.isBlank()) return emptyList()
        return parseFileContent(fileContent)
    }

    private fun parseFileContent(content: String): List<RadarData> {
        val radars = mutableListOf<RadarData>()
        val lines = content.split("\n").filter { it.isNotBlank() }
        var currentCity = ""

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("===") && trimmed.endsWith("===")) {
                currentCity = trimmed.replace("===", "").trim()
                return@forEach
            }
            val parts = trimmed.split(" - ", limit = 2)
            if (parts.size == 2) {
                val timePart = parts[0].trim()
                val locationName = parts[1].trim()
                val coordinates = RadarConfig.findCoordinatesByName(locationName)

                val radar = RadarData(
                    city = currentCity,
                    time = timePart,
                    location = locationName,
                    pageDate = LocalDateTime.now()
                )

                if (coordinates.isNotEmpty()) {
                    val first = coordinates.first()
                    radars.add(
                        radar.copy(
                            coordinate = first,
                            latitude = first.latitude,
                            longitude = first.longitude,
                            speedLimit = first.speedLimit,
                            allCoordinates = coordinates
                        )
                    )
                } else {
                    radars.add(radar)
                }
            }
        }

        return radars
            .groupBy { Triple(it.city, it.time, it.location) }
            .map { it.value.first() }
    }

    private fun cityNameExistsInHtml(htmlText: String, cityName: String): Boolean {
        if (htmlText.isBlank() || cityName.isBlank()) return false
        val normalizedHtml = stripDiacriticsLocal(htmlText.lowercase())
        val normalizedCity = stripDiacriticsLocal(cityName.lowercase())
        if (normalizedHtml.contains(normalizedCity)) return true
        val withDash = normalizedCity.replace(" ", "-")
        if (normalizedHtml.contains(withDash)) return true
        val noSpace = normalizedCity.replace(" ", "")
        if (normalizedHtml.contains(noSpace)) return true
        return false
    }

    private fun stripDiacriticsLocal(text: String): String {
        return text
            .replace('č', 'c').replace('ć', 'c')
            .replace('š', 's')
            .replace('ž', 'z')
            .replace('đ', 'd')
    }

    private fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}