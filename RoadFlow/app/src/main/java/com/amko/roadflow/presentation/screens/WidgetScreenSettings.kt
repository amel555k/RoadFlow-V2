package com.amko.roadflow.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import com.amko.roadflow.presentation.components.AppDropdown
import com.amko.roadflow.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit,
    mainViewModel: MainViewModel
) {
    val selectedCities = remember { mutableStateOf(Pair("Travnik", "Vitez")) }
    val initialCities = remember { mutableStateOf<Pair<String, String>?>(null) }
    val saveMessage = remember { mutableStateOf("") }
    val expandedCity1 = remember { mutableStateOf(false) }
    val expandedCity2 = remember { mutableStateOf(false) }

    BackHandler(enabled = expandedCity1.value || expandedCity2.value) {
        expandedCity1.value = false
        expandedCity2.value = false
    }

    LaunchedEffect(Unit) {
        val prefs = mainViewModel.getApplication<android.app.Application>()
            .getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
        val city1 = prefs.getString("city1", "Travnik") ?: "Travnik"
        val city2 = prefs.getString("city2", "Vitez") ?: "Vitez"
        val loaded = Pair(city1, city2)
        selectedCities.value = loaded
        initialCities.value = loaded
    }

    val allCities = RadarConfig.locations.map { it.name }.distinct().sorted()
    val hasChanges = initialCities.value != null && selectedCities.value != initialCities.value

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
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
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Grad 1:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                AppDropdown(
                    options = allCities.map { it to it },
                    selectedLabel = selectedCities.value.first,
                    selectedValue = selectedCities.value.first,
                    expanded = expandedCity1.value,
                    onExpandedChange = { expanded ->
                        expandedCity1.value = expanded
                        if (expanded) expandedCity2.value = false
                    },
                    onOptionSelected = { city ->
                        selectedCities.value = selectedCities.value.copy(first = city)
                        expandedCity1.value = false
                        saveMessage.value = ""
                    },
                    fieldBackground = MaterialTheme.colorScheme.surface,

                    fieldText = MaterialTheme.colorScheme.onSurface,
                    fieldTextMuted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fieldArrow = MaterialTheme.colorScheme.onSurface,
                    cornerRadius = 8.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Grad 2:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                AppDropdown(
                    options = allCities.map { it to it },
                    selectedLabel = selectedCities.value.second,
                    selectedValue = selectedCities.value.second,
                    expanded = expandedCity2.value,
                    onExpandedChange = { expanded ->
                        expandedCity2.value = expanded
                        if (expanded) expandedCity1.value = false
                    },
                    onOptionSelected = { city ->
                        selectedCities.value = selectedCities.value.copy(second = city)
                        expandedCity2.value = false
                        saveMessage.value = ""
                    },
                    fieldBackground = MaterialTheme.colorScheme.surface,
                    fieldText = MaterialTheme.colorScheme.onSurface,
                    fieldTextMuted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fieldArrow = MaterialTheme.colorScheme.onSurface,
                    cornerRadius = 8.dp
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

                if (hasChanges) {
                    Button(
                        onClick = {
                            val context = mainViewModel.getApplication<android.app.Application>()
                            val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                putString("city1", selectedCities.value.first)
                                putString("city2", selectedCities.value.second)
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

                            initialCities.value = selectedCities.value
                            saveMessage.value = "Spremljeno!"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Spremi izbor", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        if (expandedCity1.value || expandedCity2.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        expandedCity1.value = false
                        expandedCity2.value = false
                    }
            )
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