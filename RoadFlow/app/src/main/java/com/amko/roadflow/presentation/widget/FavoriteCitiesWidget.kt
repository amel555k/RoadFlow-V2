package com.amko.roadflow.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.amko.roadflow.R
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.RadarParser
import com.amko.roadflow.domain.model.RadarData
import com.amko.roadflow.presentation.screens.WidgetStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

class FavoriteCitiesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        val city1State = WidgetStateManager.city1Flow.collectAsState()
        val city2State = WidgetStateManager.city2Flow.collectAsState()

        var city1 by remember { mutableStateOf(city1State.value) }
        var city2 by remember { mutableStateOf(city2State.value) }

        val isLoading = prefs.getBoolean("widget_loading", false)
        val noInternetError = prefs.getBoolean("no_internet_error", false)
        val lastUpdateTime = prefs.getString("widget_last_update", "") ?: ""

        var radarDataCity1 by remember { mutableStateOf<List<RadarData>>(emptyList()) }
        var radarDataCity2 by remember { mutableStateOf<List<RadarData>>(emptyList()) }

        LaunchedEffect(city1, city2) {
            val allData = loadRadarDataForCities(context, city1, city2)
            radarDataCity1 = allData.filter { it.city == city1 }
            radarDataCity2 = allData.filter { it.city == city2 }
        }

        LaunchedEffect(Unit) {
            WidgetStateManager.city1Flow.collectLatest { newCity1 ->
                city1 = newCity1
            }
        }

        LaunchedEffect(Unit) {
            WidgetStateManager.city2Flow.collectLatest { newCity2 ->
                city2 = newCity2
            }
        }

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
                Text(
                    text = "RoadFlow",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                        .clickable(actionRunCallback<OpenAppCallback>())
                )

                if (isLoading) {
                    Text(
                        text = "...",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                        modifier = GlanceModifier.padding(end = 8.dp)
                    )
                }

                Image(
                    provider = ImageProvider(R.drawable.refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(actionRunCallback<WidgetRefreshCallback>())
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (noInternetError && radarDataCity1.isEmpty() && radarDataCity2.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Provjerite internet konekciju",
                        style = TextStyle(color = ColorProvider(Color.Red), fontSize = 14.sp)
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    item {
                        CitySection(city1, radarDataCity1, noInternetError)
                    }
                    item {
                        Spacer(modifier = GlanceModifier.height(12.dp))
                    }
                    item {
                        CitySection(city2, radarDataCity2, noInternetError)
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
                Text(
                    text = if (hasError) "Greška pri učitavanju" else "Nema aktivnih radara",
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

    private suspend fun loadRadarDataForCities(context: Context, city1: String, city2: String): List<RadarData> {
        return withContext(Dispatchers.IO) {
            try {
                val parser = RadarParser(context, FirebaseService())
                val allRadars = parser.getActiveRadarsAsync()
                allRadars.filter { (it.city == city1 || it.city == city2) && it.time != "INFO" }
                    .sortedBy { it.city }
            } catch (e: Exception) {
                emptyList()
            }
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