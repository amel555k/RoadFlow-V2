package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.data.local.RadarParser
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.amko.roadflow.data.local.NoInternetWithCacheException

private sealed class RadarListItem {
    data class CityHeader(val city: String) : RadarListItem()
    data class RadarEntry(val radar: RadarData) : RadarListItem()
    data class Spacer(val id: String) : RadarListItem()
}

@Composable
fun MainScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val firebaseService = remember { FirebaseService() }
    val parser = remember { RadarParser(context, firebaseService) }

    var radars by remember { mutableStateOf<List<RadarData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCanton by remember { mutableStateOf<Canton?>(Canton.Srednjobosanski) }
    var isDropdownOpen by remember { mutableStateOf(false) }
    var showNoInternet by remember { mutableStateOf(false) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    val cantonList = remember {
        listOf(
            Canton.UnskoSanski to "Unsko-sanski kanton",
            Canton.Posavski to "Posavski kanton",
            Canton.Tuzlanski to "Tuzlanski kanton",
            Canton.ZenickoDobojski to "Zeničko-dobojski kanton",
            Canton.BosanskoPodrinjski to "Bosansko-podrinjski kanton",
            Canton.Srednjobosanski to "Srednjobosanski kanton",
            Canton.HercegovackoNeretvanski to "Hercegovačko-neretvanski kanton",
            Canton.Zapadnohercegovacki to "Zapadnohercegovački kanton",
            Canton.Sarajevo to "Kanton Sarajevo",
            Canton.Kanton10 to "Kanton 10",
            Canton.BrckoDistrikt to "Brčko distrikt"
        )
    }

    val selectedCantonLabel = cantonList.firstOrNull { it.first == selectedCanton }?.second ?: ""

    val flatList = remember(radars, selectedCanton) {
        val filtered = if (selectedCanton == null) {
            radars
        } else {
            val citiesInCanton = RadarConfig.locations
                .filter { it.canton == selectedCanton }
                .map { it.name }
                .toHashSet()
            radars.filter { citiesInCanton.contains(it.city) }
        }

        val grouped = filtered.groupBy { it.city }

        buildList {
            grouped.forEach { (city, cityRadars) ->
                add(RadarListItem.CityHeader(city))
                cityRadars.forEach { radar ->
                    add(RadarListItem.RadarEntry(radar))
                }
                add(RadarListItem.Spacer(id = "spacer_$city"))
            }
        }
    }

    fun loadData() {
        scope.launch {
            isLoading = true
            try {
                radars = parser.parseAllLocationsAsync()
                currentDate = LocalDate.now()
            } catch (e: NoInternetWithCacheException) {
                radars = e.cachedRadars
                currentDate = LocalDate.now()
                showNoInternet = true
            } catch (_: Exception) {
                showNoInternet = true
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFD9D9D9))) {

        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF212143))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Meni",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable { onOpenDrawer() }
                    )
                    Text(
                        text = "RoadFlow",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = currentDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2E2E5E))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedCantonLabel,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isDropdownOpen) "▲" else "▼",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { isDropdownOpen = !isDropdownOpen }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF212143))
                }
            } else if (flatList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nema radara za odabrani kanton.",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(
                        items = flatList,
                        key = { item ->
                            when (item) {
                                is RadarListItem.CityHeader -> "header_${item.city}"
                                is RadarListItem.RadarEntry -> "radar_${item.radar.city}_${item.radar.time}_${item.radar.location}"
                                is RadarListItem.Spacer -> item.id
                            }
                        },
                        contentType = { item ->
                            when (item) {
                                is RadarListItem.CityHeader -> 0
                                is RadarListItem.RadarEntry -> 1
                                is RadarListItem.Spacer -> 2
                            }
                        }
                    ) { item ->
                        when (item) {
                            is RadarListItem.CityHeader -> {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 10.dp)
                                        .background(Color(0xFF212143), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = item.city,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            is RadarListItem.RadarEntry -> {
                                RadarItem(radar = item.radar)
                            }
                            is RadarListItem.Spacer -> {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }

        if (isDropdownOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isDropdownOpen = false }
            )
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 100.dp, end = 16.dp)
                    .width(220.dp),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(cantonList) { (canton, label) ->
                        val isSelected = canton == selectedCanton
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCanton = canton
                                    isDropdownOpen = false
                                }
                                .background(if (isSelected) Color(0xFFE8E8F5) else Color.White)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF212143) else Color.Black
                            )
                        }
                    }
                }
            }
        }

        if (showNoInternet) {
            Dialog(onDismissRequest = { showNoInternet = false }) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Nema internet konekcije",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF212143)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Molimo provjerite da li su uključeni WiFi ili mobilni podaci.",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showNoInternet = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212143)),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("U REDU", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarItem(radar: RadarData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFD9D9D9))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = radar.time,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black,
                    modifier = Modifier.width(70.dp)
                )
                Text(
                    text = radar.location,
                    fontSize = 14.sp,
                    color = Color.Black,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}