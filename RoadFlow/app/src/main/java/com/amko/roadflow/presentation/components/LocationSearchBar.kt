package com.amko.roadflow.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.maplibre.android.geometry.LatLng

data class SearchResult(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

private fun shortenLocationName(fullName: String): String {
    val parts = fullName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return fullName

    val words = parts[0].split(" ").filter { it.isNotEmpty() }
    return if (words.size > 3) {
        words.take(3).joinToString(" ")
    } else {
        parts[0]
    }
}

@Composable
fun LocationSearchBar(
    onLocationSelected: (LatLng, String) -> Unit,
    onExpandedChange: (Boolean) -> Unit = {},
    onLocationCleared: () -> Unit = {},
    selectedLocationName: String? = null,
    isPickingOnMap: Boolean = false,
    onPickOnMapStart: () -> Unit = {},
    onPickOnMapConfirm: () -> Unit = {},
    onPickOnMapCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var expandedFromSelection by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 3) {
            searchResults = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(500)
        searchResults = searchLocations(query)
        isLoading = false
    }

    Box(modifier = modifier) {
        if (isPickingOnMap) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onPickOnMapCancel() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Otkaži biranje na karti",
                            tint = Color(0xFF004E5A)
                        )
                    }

                    Text(
                        text = "Odaberi destinaciju na karti",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = { onPickOnMapConfirm() }
                    ) {
                        Text(
                            text = "OK",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF004E5A)
                        )
                    }
                }
            }
        } else if (!isExpanded) {
            if (selectedLocationName != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isExpanded = true
                                expandedFromSelection = true
                                onExpandedChange(true)
                                onLocationCleared()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Otvori pretragu",
                                tint = Color(0xFF004E5A)
                            )
                        }

                        Text(
                            text = "Vaša lokacija → ${shortenLocationName(selectedLocationName)}",
                            fontSize = 14.sp,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onLocationCleared() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Otkaži destinaciju",
                                tint = Color.Gray
                            )
                        }
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = {
                        isExpanded = true
                        onExpandedChange(true)
                    },
                    containerColor = Color.White,
                    contentColor = Color(0xFF004E5A),
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Pretraži adrese",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isExpanded = false
                                onExpandedChange(false)
                                query = ""
                                searchResults = emptyList()
                                if (expandedFromSelection) {
                                    expandedFromSelection = false
                                    onLocationCleared()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Zatvori pretragu",
                                tint = Color(0xFF004E5A)
                            )
                        }

                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Unesite adresu ili mjesto...", fontSize = 14.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp),
                                color = Color(0xFF004E5A),
                                strokeWidth = 2.dp
                            )
                        } else if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Očisti unos",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isExpanded = false
                                onExpandedChange(false)
                                query = ""
                                searchResults = emptyList()
                                expandedFromSelection = false
                                onPickOnMapStart()
                            }
                            .padding(start = 64.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "Odaberi destinaciju na karti",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF004E5A)
                        )
                    }

                    AnimatedVisibility(
                        visible = searchResults.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 240.dp)
                            ) {
                                items(searchResults) { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onLocationSelected(
                                                    LatLng(result.lat, result.lon),
                                                    result.displayName
                                                )
                                                isExpanded = false
                                                expandedFromSelection = false
                                                onExpandedChange(false)
                                                query = ""
                                                searchResults = emptyList()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFF004E5A),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = result.displayName,
                                            fontSize = 13.sp,
                                            color = Color.Black,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun searchLocations(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
    val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=5&countrycodes=ba"

    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "RoadFlowAndroidApp/1.0")
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(body)
            val results = mutableListOf<SearchResult>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val name = item.optString("display_name")
                val lat = item.optString("lat").toDoubleOrNull()
                val lon = item.optString("lon").toDoubleOrNull()

                if (lat != null && lon != null) {
                    results.add(SearchResult(name, lat, lon))
                }
            }
            results
        }
    } catch (e: Exception) {
        emptyList()
    }
}