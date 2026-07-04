package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.R
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit,
    mainViewModel: MainViewModel
) {
    val selectedCities = remember { mutableStateOf(Pair("Travnik", "Vitez")) }
    val saveMessage = remember { mutableStateOf("") }
    val expandedCity1 = remember { mutableStateOf(false) }
    val expandedCity2 = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = mainViewModel.getApplication<android.app.Application>()
            .getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
        val city1 = prefs.getString("favorite_city_1", "Travnik") ?: "Travnik"
        val city2 = prefs.getString("favorite_city_2", "Vitez") ?: "Vitez"
        selectedCities.value = Pair(city1, city2)
    }

    val allCities = RadarConfig.locations.map { it.name }.distinct().sorted()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD9D9D9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF212143))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.back),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Postavke widgeta",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            CityDropdown(
                label = "Grad 1",
                selectedCity = selectedCities.value.first,
                allCities = allCities,
                expanded = expandedCity1.value,
                onExpandedChange = { expandedCity1.value = it },
                onCitySelected = { city ->
                    selectedCities.value = selectedCities.value.copy(first = city)
                    expandedCity1.value = false
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            CityDropdown(
                label = "Grad 2",
                selectedCity = selectedCities.value.second,
                allCities = allCities,
                expanded = expandedCity2.value,
                onExpandedChange = { expandedCity2.value = it },
                onCitySelected = { city ->
                    selectedCities.value = selectedCities.value.copy(second = city)
                    expandedCity2.value = false
                }
            )

            if (saveMessage.value.isNotEmpty()) {
                Text(
                    text = saveMessage.value,
                    color = Color(0xFF1B5E20),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val context = mainViewModel.getApplication<android.app.Application>()
                    val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("favorite_city_1", selectedCities.value.first)
                        putString("favorite_city_2", selectedCities.value.second)
                        apply()
                    }

                    WidgetStateManager.updateCities(selectedCities.value.first, selectedCities.value.second)

                    val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                    val componentName = android.content.ComponentName(
                        context,
                        com.amko.roadflow.presentation.widget.FavoriteCitiesWidgetReceiver::class.java
                    )
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    if (appWidgetIds.isNotEmpty()) {
                        val intent = android.content.Intent(context, com.amko.roadflow.presentation.widget.FavoriteCitiesWidgetReceiver::class.java).apply {
                            action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                        }
                        context.sendBroadcast(intent)
                    }

                    saveMessage.value = "Spremljeno!"
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212143))
            ) {
                Text("Spremi izbor", color = Color.White)
            }
        }
    }
}

@Composable
fun CityDropdown(
    label: String,
    selectedCity: String,
    allCities: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCitySelected: (String) -> Unit
) {
    Column {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp),
            color = Color.DarkGray
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onExpandedChange(!expanded) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedCity,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = Color.Black
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp),
                tint = Color.Black
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .heightIn(max = 400.dp)
                .background(Color.White)
        ) {
            allCities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city, color = Color.Black) },
                    onClick = { onCitySelected(city) }
                )
            }
        }
    }
}

object WidgetStateManager {
    val city1Flow = MutableStateFlow("Travnik")
    val city2Flow = MutableStateFlow("Vitez")

    fun updateCities(newCity1: String, newCity2: String) {
        city1Flow.value = newCity1
        city2Flow.value = newCity2
    }
}