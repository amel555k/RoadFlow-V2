package com.amko.roadflow.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import java.io.File
import java.time.LocalDate

class FavoriteCitiesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sharedPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        if (!sharedPrefs.contains("cities_initialized")) {
            sharedPrefs.edit()
                .putString("city_1", "Travnik")
                .putString("city_2", "Vitez")
                .putBoolean("cities_initialized", true)
                .apply()
        }

        val city1 = sharedPrefs.getString("city_1", "Travnik") ?: "Travnik"
        val city2 = sharedPrefs.getString("city_2", "Vitez") ?: "Vitez"

        val parser = RadarParser(context, FirebaseService())
        val filePath = File(context.filesDir, "lista.txt")
        val todayDate = LocalDate.now()

        val fileIsValid = filePath.exists() &&
                LocalDate.ofEpochDay(filePath.lastModified() / 86400000L) == todayDate

        val allRadars = if (fileIsValid) {
            try {
                parser.getActiveRadarsAsync()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            try {
                var lastEmit = emptyList<RadarData>()
                parser.parseAllLocationsAsFlow().collect { lastEmit = it }
                lastEmit
            } catch (e: Exception) {
                try {
                    parser.getActiveRadarsAsync()
                } catch (e2: Exception) {
                    emptyList()
                }
            }
        }

        val data1 = allRadars.filter { it.city == city1 }
        val data2 = allRadars.filter { it.city == city2 }

        provideContent {
            MyWidgetUI(listOf(city1 to data1, city2 to data2))
        }
    }

    @Composable
    private fun MyWidgetUI(citiesData: List<Pair<String, List<RadarData>>>) {
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
                )

                Image(
                    provider = ImageProvider(R.drawable.refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(actionRunCallback<WidgetRefreshCallback>())
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(citiesData) { (cityName, radars) ->
                    CitySection(cityName, radars)
                    Spacer(modifier = GlanceModifier.height(12.dp))
                }
            }
        }
    }

    @Composable
    private fun CitySection(cityName: String, radars: List<RadarData>) {
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
                    text = "Nema podataka",
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
}