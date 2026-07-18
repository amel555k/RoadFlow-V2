package com.amko.roadflow.presentation.widget

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.amko.roadflow.R
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.domain.model.RadarData
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FavoriteCitiesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("WidgetDebug", "provideGlance: POZVAN za id=$id")

        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val city1 = prefs.getString("city1", "Travnik") ?: "Travnik"
        val city2 = prefs.getString("city2", "Vitez") ?: "Vitez"
        var lastUpdateTime = prefs.getString("widget_last_update", "") ?: ""

        Log.d("WidgetDebug", "provideGlance: procitano iz prefs -> city1=$city1, city2=$city2, lastUpdateTime=$lastUpdateTime")

        val file = File(context.filesDir, "widget.txt")
        val isCachedForToday = file.exists() && LocalDate.ofEpochDay(file.lastModified() / 86400000L) == LocalDate.now()
        val internetAvailable = isInternetAvailable(context)

        Log.d("WidgetDebug", "provideGlance: isCachedForToday=$isCachedForToday, internetAvailable=$internetAvailable")

        var (allData, fetchFailed) = readCachedRadars(context, city1, city2, isCachedForToday)

        if (internetAvailable && (!isCachedForToday || allData.isEmpty())) {
            Log.d("WidgetDebug", "provideGlance: potreban fetch (nije cached za danas ili prazno), pokrecem inline fetch")
            val fetchResult = fetchAndSaveWidgetData(context, city1, city2)
            allData = fetchResult.first
            fetchFailed = fetchResult.second
            if (!fetchFailed) {
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                lastUpdateTime = LocalDateTime.now().format(formatter)
                prefs.edit().putString("widget_last_update", lastUpdateTime).apply()
            }
        } else if (!internetAvailable && (!isCachedForToday || allData.isEmpty())) {
            Log.d("WidgetDebug", "provideGlance: nema interneta i nema validnog kesa -> fetchFailed=true")
            fetchFailed = true
        }

        val radarDataCity1 = allData.filter { it.city == city1 }
        val radarDataCity2 = allData.filter { it.city == city2 }

        Log.d("WidgetDebug", "provideGlance: FINALNO -> radarDataCity1=${radarDataCity1.size}, radarDataCity2=${radarDataCity2.size}, fetchFailed=$fetchFailed")

        val fileModificationDate = if (file.exists()) {
            val date = LocalDate.ofEpochDay(file.lastModified() / 86400000L)
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            date.format(formatter)
        } else {
            ""
        }

        provideContent {
            WidgetContent(
                city1 = city1,
                city2 = city2,
                lastUpdateTime = lastUpdateTime,
                radarDataCity1 = radarDataCity1,
                radarDataCity2 = radarDataCity2,
                fetchFailed = fetchFailed,
                fileModificationDate = fileModificationDate
            )
        }
    }

    @Composable
    private fun WidgetContent(
        city1: String,
        city2: String,
        lastUpdateTime: String,
        radarDataCity1: List<RadarData>,
        radarDataCity2: List<RadarData>,
        fetchFailed: Boolean,
        fileModificationDate: String
    ) {
        val isErrorState = fetchFailed && radarDataCity1.isEmpty() && radarDataCity2.isEmpty()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1A1A2E)))
                .padding(10.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headerText = if (fileModificationDate.isNotEmpty()) "RoadFlow ($fileModificationDate)" else "RoadFlow"

                Text(
                    text = headerText,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                        .clickable(actionRunCallback<OpenAppCallback>())
                )

                if (!isErrorState) {
                    Image(
                        provider = ImageProvider(R.drawable.refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier
                            .size(24.dp)
                            .clickable(actionRunCallback<WidgetRefreshCallback>())
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (isErrorState) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Provjerite internet konekciju",
                            style = TextStyle(color = ColorProvider(Color.Red), fontSize = 14.sp)
                        )
                        Spacer(modifier = GlanceModifier.height(16.dp))
                        Image(
                            provider = ImageProvider(R.drawable.refresh),
                            contentDescription = "Refresh",
                            modifier = GlanceModifier
                                .size(48.dp)
                                .clickable(actionRunCallback<WidgetRefreshCallback>())
                        )
                    }
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    item {
                        CitySection(city1, radarDataCity1, fetchFailed)
                    }
                    item {
                        Spacer(modifier = GlanceModifier.height(12.dp))
                    }
                    item {
                        CitySection(city2, radarDataCity2, fetchFailed)
                    }
                    if (lastUpdateTime.isNotEmpty()) {
                        item {
                            Spacer(modifier = GlanceModifier.height(8.dp))
                            Text(
                                text = "Ažurirano: $lastUpdateTime",
                                style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 8.sp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CitySection(cityName: String, radars: List<RadarData>, hasError: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Color.White.copy(alpha = 0.06f)))
                .padding(10.dp)
        ) {
            Text(
                text = cityName.uppercase(),
                style = TextStyle(
                    color = ColorProvider(Color.Cyan),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            if (radars.isEmpty()) {
                val statusText = if (hasError) "Greška pri učitavanju" else "Nema aktivnih radara"

                Text(
                    text = statusText,
                    style = TextStyle(
                        color = ColorProvider(Color.Gray),
                        fontSize = 14.sp
                    )
                )
            } else {
                radars.take(5).forEach { radar ->
                    Column(modifier = GlanceModifier.padding(top = 6.dp)) {
                        Text(
                            text = radar.location,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = radar.time,
                            style = TextStyle(
                                color = ColorProvider(Color.LightGray),
                                fontSize = 18.sp
                            )
                        )
                    }
                }
            }
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun readCachedRadars(context: Context, city1: String, city2: String, isCachedForToday: Boolean): Pair<List<RadarData>, Boolean> {
        val file = File(context.filesDir, "widget.txt")
        if (!file.exists()) return Pair(emptyList(), true)

        val radars = mutableListOf<RadarData>()
        file.readLines().forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 3) {
                radars.add(RadarData(city = parts[0], time = parts[1], location = parts[2], pageDate = LocalDateTime.now()))
            }
        }

        val filtered = radars.filter { (it.city == city1 || it.city == city2) && it.time != "INFO" }
        Log.d("WidgetDebug", "readCachedRadars: procitano ${radars.size} stavki iz kesa, filtrirano ${filtered.size}")

        if (!isCachedForToday && filtered.isEmpty()) {
            return Pair(emptyList(), true)
        }

        return Pair(filtered, false)
    }

    suspend fun fetchAndSaveWidgetData(context: Context, city1: String, city2: String): Pair<List<RadarData>, Boolean> {
        return try {
            Log.d("WidgetDebug", "fetchAndSaveWidgetData: ZAPOCETO za city1=$city1, city2=$city2")

            val firebaseService = FirebaseService()
            val parser = com.amko.roadflow.data.local.RadarParser(context, firebaseService)
            val selectedCities = listOf(city1, city2).filter { it.isNotBlank() }.distinct()
            val today = LocalDate.now()

            val widgetDataLines = mutableListOf<String>()
            val firebaseRadars = firebaseService.getFirebaseRadarsAsync(today)
            Log.d("WidgetDebug", "fetchAndSaveWidgetData: Dohvaceno ${firebaseRadars.size} radara sa Firebase-a")

            val allFetchedRadars = mutableListOf<RadarData>()

            for (cityName in selectedCities) {
                val location = RadarConfig.locations.firstOrNull { it.name == cityName }
                if (location == null) {
                    Log.d("WidgetDebug", "fetchAndSaveWidgetData: Grad $cityName nije pronadjen u RadarConfig!")
                    continue
                }

                if (location.fromFirebase) {
                    val cityRadars = firebaseRadars.filter { it.city == cityName }
                    cityRadars.forEach { radar ->
                        widgetDataLines.add("${radar.city}|${radar.time}|${radar.location}")
                    }
                    allFetchedRadars.addAll(cityRadars)
                    Log.d("WidgetDebug", "fetchAndSaveWidgetData: Grad $cityName - dodano ${cityRadars.size} radara sa Firebase")
                } else {
                    val cityRadars = mutableListOf<RadarData>()
                    for (linkId in location.possibleIds) {
                        val radarsFromLink = parser.parseSingleIdWithErrorHandlingAsyncPublic(cityName, linkId, location.mapEnabled)
                        val todayRadars = radarsFromLink.filter {
                            (it.pageDate != null && it.pageDate.toLocalDate() == today) || it.time == "INFO"
                        }
                        cityRadars.addAll(todayRadars)
                    }
                    val dedupedCityRadars = cityRadars
                        .filter { it.time != "INFO" }
                        .groupBy { Triple(it.city, it.time, it.location) }
                        .map { it.value.first() }

                    dedupedCityRadars.forEach { radar ->
                        widgetDataLines.add("${radar.city}|${radar.time}|${radar.location}")
                    }
                    allFetchedRadars.addAll(dedupedCityRadars)
                    Log.d("WidgetDebug", "fetchAndSaveWidgetData: Grad $cityName - dodano ${dedupedCityRadars.size} radara sa Weba")
                }
            }

            val file = File(context.filesDir, "widget.txt")
            val content = widgetDataLines.joinToString("\n")
            file.writeText(content)
            Log.d("WidgetDebug", "fetchAndSaveWidgetData: widget.txt upisan. Velicina: ${file.length()} bytes")

            Pair(allFetchedRadars, false)
        } catch (e: Exception) {
            Log.d("WidgetDebug", "fetchAndSaveWidgetData: EXCEPTION: ${e.message}")
            Pair(emptyList(), true)
        }
    }
}

class OpenAppCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}

class WidgetRefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d("WidgetDebug", "WidgetRefreshCallback: onAction ZAPOCETO")
        FavoriteCitiesWidget().updateAll(context)
        Log.d("WidgetDebug", "WidgetRefreshCallback: onAction KRAJ")
    }
}