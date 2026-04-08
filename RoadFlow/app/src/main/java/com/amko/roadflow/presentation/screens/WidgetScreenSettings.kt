package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.glance.appwidget.updateAll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amko.roadflow.R
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.presentation.viewmodel.MainViewModel
import com.amko.roadflow.presentation.widget.FavoriteCitiesWidget
import kotlinx.coroutines.launch

@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit,
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("widget_prefs", 0) }

    var city1 by remember { mutableStateOf(sharedPrefs.getString("city_1", "Travnik") ?: "Travnik") }
    var city2 by remember { mutableStateOf(sharedPrefs.getString("city_2", "Vitez") ?: "Vitez") }

    var showSearch by remember { mutableStateOf(false) }
    var activeSelection by remember { mutableIntStateOf(1) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLocations = remember(searchQuery) {
        RadarConfig.locations.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFD9D9D9))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF212143))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.back),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Widget Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

        }

        if (showSearch) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Traži grad...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    items(filteredLocations) { location ->
                        Text(
                            text = location.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (activeSelection == 1) {
                                        city1 = location.name
                                        sharedPrefs.edit().putString("city_1", location.name).apply()
                                    } else {
                                        city2 = location.name
                                        sharedPrefs.edit().putString("city_2", location.name).apply()
                                    }

                                    scope.launch {
                                        FavoriteCitiesWidget().updateAll(context)
                                    }

                                    showSearch = false
                                    searchQuery = ""
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            fontSize = 16.sp
                        )
                        HorizontalDivider(color = Color.LightGray)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(text = "Odaberi gradove za widget:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

                WidgetCityPicker("Grad 1", city1) {
                    activeSelection = 1
                    showSearch = true
                }

                Spacer(modifier = Modifier.height(12.dp))

                WidgetCityPicker("Grad 2", city2) {
                    activeSelection = 2
                    showSearch = true
                }

            }
        }
    }
}

@Composable
fun WidgetCityPicker(label: String, cityName: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = label, fontSize = 12.sp, color = Color.Gray)
                Text(text = cityName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "Promijeni", color = Color(0xFF212143), fontWeight = FontWeight.Bold)
        }
    }
}