package com.amko.roadflow.presentation.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amko.roadflow.R
import com.amko.roadflow.data.local.Secrets.MAP_API_KEY
import com.amko.roadflow.domain.model.RadarData
import com.amko.roadflow.presentation.components.RadarInfoCard
import com.amko.roadflow.presentation.viewmodel.MapViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import androidx.activity.compose.rememberLauncherForActivityResult

private const val RADAR_ICON_ID = "radar-icon"
private const val RADAR_ICON_STACIONARNI_ID = "radar-icon-stacionarni"
private const val USER_ICON_ID = "user-icon"

@Composable
fun MapScreen(
    onOpenDrawer: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val coroutineScope = rememberCoroutineScope()

    val activeRadars by viewModel.activeRadars.collectAsState()
    val userLocation by viewModel.locationService.location.collectAsState()
    val userHeading by viewModel.locationService.heading.collectAsState()
    val isActiveTracking by viewModel.locationService.isActiveTracking.collectAsState()

    val mapViewRef = remember { MapView(context) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var selectedFilter by remember { mutableStateOf("active") }
    var selectedRadar by remember { mutableStateOf<RadarData?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var userSymbol by remember { mutableStateOf<Symbol?>(null) }
    var didInitialZoom by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.locationService.startPassiveTracking()
        }
    }

    LaunchedEffect(Unit) {
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            val lastKnown = viewModel.locationService.getLastKnownLocation()
            if (lastKnown != null) {
                viewModel.locationService.setInitialLocation(lastKnown)
            }
            viewModel.locationService.startPassiveTracking()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    var trackingButtonScale by remember { mutableStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = trackingButtonScale,
        animationSpec = tween(durationMillis = 150),
        label = "button_scale"
    )

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
        val sm = symbolManager ?: return@LaunchedEffect
        styleRef ?: return@LaunchedEffect
        val savedLatLng = userSymbol?.latLng
        val savedRotate = userSymbol?.iconRotate ?: 0f

        sm.deleteAll()
        userSymbol = null

        activeRadars.forEachIndexed { index, radar ->
            val lat = radar.latitude ?: return@forEachIndexed
            val lng = radar.longitude ?: return@forEachIndexed
            val iconId = if (radar.coordinate?.stacionaran == true)
                RADAR_ICON_STACIONARNI_ID else RADAR_ICON_ID
            sm.create(
                SymbolOptions()
                    .withLatLng(LatLng(lat, lng))
                    .withIconImage(iconId)
                    .withIconSize(1.0f)
                    .withData(com.google.gson.JsonPrimitive(index))
            )
        }

        if (savedLatLng != null) {
            userSymbol = sm.create(
                SymbolOptions()
                    .withLatLng(savedLatLng)
                    .withIconImage(USER_ICON_ID)
                    .withIconSize(1.2f)
                    .withIconRotate(savedRotate)
            )
        }

        val features = activeRadars.mapNotNull { radar ->
            val lat = radar.latitude ?: return@mapNotNull null
            val lng = radar.longitude ?: return@mapNotNull null
            createCircleFeature(lng, lat, 200.0)
        }
        mapRef?.style?.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(
            "radar-zones-source"
        )?.setGeoJson(
            org.maplibre.geojson.FeatureCollection.fromFeatures(features)
        )
    }

    LaunchedEffect(userLocation, userHeading, isMapReady) {
        val map = mapRef ?: return@LaunchedEffect
        val sm = symbolManager ?: return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect

        val existing = userSymbol
        val rotation = if (isActiveTracking) userHeading.toFloat() else 0f

        if (existing != null) {
            existing.latLng = LatLng(loc.latitude, loc.longitude)
            existing.iconRotate = rotation
            sm.update(existing)
        } else {
            userSymbol = sm.create(
                SymbolOptions()
                    .withLatLng(LatLng(loc.latitude, loc.longitude))
                    .withIconImage(USER_ICON_ID)
                    .withIconSize(1.2f)
                    .withIconRotate(rotation)
            )
        }

        if (!didInitialZoom) {
            didInitialZoom = true
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(loc.latitude, loc.longitude))
                        .zoom(14.0)
                        .tilt(0.0)
                        .bearing(0.0)
                        .build()
                ), 1000
            )
        } else if (isActiveTracking) {
            map.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(loc.latitude, loc.longitude))
                        .bearing(userHeading)
                        .build()
                ), 300
            )
        }
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

                            style.addImage(RADAR_ICON_ID, createRadarBitmap(context, false))
                            style.addImage(RADAR_ICON_STACIONARNI_ID, createRadarBitmap(context, true))
                            style.addImage(USER_ICON_ID, createUserBitmap(context))

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    "radar-zones-source",
                                    org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.FillLayer("radar-zones-fill", "radar-zones-source").apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.fillColor(Color.parseColor("#2196F3")),
                                        org.maplibre.android.style.layers.PropertyFactory.fillOpacity(0.2f)
                                    )
                                }
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.LineLayer("radar-zones-outline", "radar-zones-source").apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#1976D2")),
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
                                    LatLng(43.8563, 18.4131), 12.0
                                )
                            )

                            isMapReady = true
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
                Button(
                    onClick = {
                        coroutineScope.launch {
                            trackingButtonScale = 0.85f
                            delay(150)
                            trackingButtonScale = 1f

                            val map = mapRef ?: return@launch
                            if (isActiveTracking) {
                                viewModel.locationService.stopActiveTracking()
                                viewModel.locationService.startPassiveTracking()

                                map.animateCamera(
                                    CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.Builder()
                                            .zoom(13.0)
                                            .tilt(0.0)
                                            .bearing(0.0)
                                            .build()
                                    ), 800
                                )
                            } else {
                                viewModel.locationService.stopPassiveTracking()
                                viewModel.locationService.startActiveTracking()

                                val loc = viewModel.locationService.location.value
                                if (loc != null) {
                                    map.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(LatLng(loc.latitude, loc.longitude))
                                                .zoom(18.0)
                                                .tilt(45.0)
                                                .bearing(userHeading)
                                                .build()
                                        ), 1200
                                    )
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActiveTracking)
                            androidx.compose.ui.graphics.Color(0xFFF44336)
                        else
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    contentPadding = PaddingValues(horizontal = 30.dp, vertical = 15.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 140.dp)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                        }
                ) {
                    Text(
                        text = if (isActiveTracking) "ZAVRŠI" else "KRENI",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }

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
                    enabled = !isActiveTracking,
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

private fun createUserBitmap(context: android.content.Context): Bitmap {
    val drawable = context.getDrawable(R.drawable.user_marker)
    if (drawable != null) {
        val size = 80
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

    val size = 60
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, bgPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, borderPaint)

    val path = android.graphics.Path()
    path.moveTo(size / 2f, 8f)
    path.lineTo(size / 2f - 10f, size / 2f + 10f)
    path.lineTo(size / 2f, size / 2f)
    path.lineTo(size / 2f + 10f, size / 2f + 10f)
    path.close()
    canvas.drawPath(path, arrowPaint)

    return bmp
}

private fun createRadarBitmap(context: android.content.Context, isStacionarni: Boolean): Bitmap {
    val drawableId = if (isStacionarni) R.drawable.stac_radar else R.drawable.active_radar
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId)

    if (drawable != null) {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

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
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive)
                androidx.compose.ui.graphics.Color(0xFF4D7079)
            else
                androidx.compose.ui.graphics.Color(0xFF1E2736),
            disabledContainerColor = androidx.compose.ui.graphics.Color(0xFF1E2736).copy(alpha = 0.5f)
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