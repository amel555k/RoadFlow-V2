package com.amko.roadflow.presentation.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amko.roadflow.data.local.Secrets.MAP_API_KEY
import com.amko.roadflow.domain.model.RadarData
import com.amko.roadflow.presentation.components.RadarInfoCard
import com.amko.roadflow.presentation.viewmodel.MapViewModel
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

private const val RADAR_ICON_ID = "radar-icon"
private const val RADAR_ICON_STACIONARNI_ID = "radar-icon-stacionarni"

@Composable
fun MapScreen(
    onOpenDrawer: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activeRadars by viewModel.activeRadars.collectAsState()

    val mapViewRef = remember { MapView(context) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var selectedFilter by remember { mutableStateOf("active") }
    var selectedRadar by remember { mutableStateOf<RadarData?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapViewRef.onCreate(null)
                Lifecycle.Event.ON_START -> mapViewRef.onStart()
                Lifecycle.Event.ON_RESUME -> mapViewRef.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef.onPause()
                Lifecycle.Event.ON_STOP -> mapViewRef.onStop()
                Lifecycle.Event.ON_DESTROY -> mapViewRef.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(activeRadars, styleRef) {
        val map = mapRef ?: return@LaunchedEffect
        styleRef ?: return@LaunchedEffect
        val sm = symbolManager ?: return@LaunchedEffect
        renderRadarPins(sm, map, activeRadars)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF212143))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Meni",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(end = 16.dp).clickable { onOpenDrawer() }
            )
            Text(
                text = "Mape",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapViewRef },
                update = { view ->
                    view.getMapAsync { map ->
                        mapRef = map

                        map.setMinZoomPreference(7.0)
                        map.setMaxZoomPreference(18.0)

                        val bihBounds = LatLngBounds.Builder()
                            .include(LatLng(45.17, 15.60))
                            .include(LatLng(42.56, 19.50))
                            .build()
                        map.setLatLngBoundsForCameraTarget(bihBounds)

                        map.setStyle(MAP_API_KEY) { style ->
                            styleRef = style

                            style.addImage(RADAR_ICON_ID, createRadarBitmap(isStacionarni = false))
                            style.addImage(RADAR_ICON_STACIONARNI_ID, createRadarBitmap(isStacionarni = true))

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    "radar-zones-source",
                                    org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.FillLayer("radar-zones-fill", "radar-zones-source").apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.fillColor(
                                            Color.parseColor("#2196F3")
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.fillOpacity(0.2f)
                                    )
                                }
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.LineLayer("radar-zones-outline", "radar-zones-source").apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.lineColor(
                                            Color.parseColor("#1976D2")
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(2f)
                                    )
                                }
                            )

                            val sm = SymbolManager(view, map, style).also {
                                it.iconAllowOverlap = true
                                it.textAllowOverlap = true
                                symbolManager = it
                            }

                            sm.addClickListener { symbol ->
                                val index = symbol.data?.asInt ?: return@addClickListener false
                                selectedRadar = activeRadars.getOrNull(index)
                                true
                            }

                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(44.2264, 17.6658), 7.5
                                )
                            )
                        }
                    }
                }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
                horizontalAlignment = Alignment.End
            ) {
                FilterButton(
                    text = "AKTIVNI",
                    isActive = selectedFilter == "active",
                    onClick = {
                        selectedFilter = "active"
                        viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE)
                    }
                )
                FilterButton(
                    text = "DANAS",
                    isActive = selectedFilter == "today",
                    onClick = {
                        selectedFilter = "today"
                        viewModel.setFilter(MapViewModel.RadarFilter.TODAY)
                    }
                )
            }

            selectedRadar?.let { radar ->
                RadarInfoCard(
                    radar = radar,
                    onDismiss = { selectedRadar = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

private fun renderRadarPins(
    symbolManager: SymbolManager,
    map: MapLibreMap,
    radars: List<RadarData>
) {
    symbolManager.deleteAll()

    radars.forEachIndexed { index, radar ->
        val lat = radar.latitude ?: return@forEachIndexed
        val lng = radar.longitude ?: return@forEachIndexed

        val iconId = if (radar.coordinate?.stacionaran == true)
            RADAR_ICON_STACIONARNI_ID else RADAR_ICON_ID

        symbolManager.create(
            SymbolOptions()
                .withLatLng(LatLng(lat, lng))
                .withIconImage(iconId)
                .withIconSize(1.0f)
                .withData(com.google.gson.JsonPrimitive(index))
        )
    }

    val features = radars.mapNotNull { radar ->
        val lat = radar.latitude ?: return@mapNotNull null
        val lng = radar.longitude ?: return@mapNotNull null
        createCircleFeature(lng, lat, 200.0)
    }

    map.style?.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(
        "radar-zones-source"
    )?.setGeoJson(
        org.maplibre.geojson.FeatureCollection.fromFeatures(features)
    )
}

private fun createCircleFeature(
    lng: Double,
    lat: Double,
    radiusMeters: Double,
    points: Int = 64
): org.maplibre.geojson.Feature {
    val km = radiusMeters / 1000.0
    val distanceX = km / (111.320 * Math.cos(Math.toRadians(lat)))
    val distanceY = km / 110.574

    val coordinates = mutableListOf<org.maplibre.geojson.Point>()
    for (i in 0 until points) {
        val theta = (i.toDouble() / points) * (2 * Math.PI)
        val x = distanceX * Math.cos(theta)
        val y = distanceY * Math.sin(theta)
        coordinates.add(org.maplibre.geojson.Point.fromLngLat(lng + x, lat + y))
    }
    coordinates.add(coordinates[0])

    return org.maplibre.geojson.Feature.fromGeometry(
        org.maplibre.geojson.Polygon.fromLngLats(listOf(coordinates))
    )
}

private fun createRadarBitmap(isStacionarni: Boolean): Bitmap {
    val size = 60
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val bgColor = if (isStacionarni) Color.parseColor("#1565C0") else Color.parseColor("#1E88E5")

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, bgPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, borderPaint)

    return bmp
}

@Composable
private fun FilterButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive)
                androidx.compose.ui.graphics.Color(0xFF4D7079)
            else
                androidx.compose.ui.graphics.Color(0xFF1E2736)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
        contentPadding = PaddingValues(horizontal = 25.dp, vertical = 15.dp),
        modifier = Modifier.defaultMinSize(minWidth = 120.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}