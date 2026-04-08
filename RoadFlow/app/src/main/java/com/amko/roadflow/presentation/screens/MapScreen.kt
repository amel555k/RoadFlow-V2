package com.amko.roadflow.presentation.screens

import android.graphics.Color
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
import com.amko.roadflow.data.local.Secrets.MAP_API_KEY
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
import com.amko.roadflow.presentation.components.FilterButton
import com.amko.roadflow.utils.createUserBitmap
import com.amko.roadflow.utils.createCircleFeature
import com.amko.roadflow.utils.createRadarBitmap
import com.amko.roadflow.presentation.components.SpeedOverlay
import com.amko.roadflow.presentation.components.NoConnectionDialog
private const val RADAR_ICON_ID = "radar-icon"
private const val RADAR_ICON_STACIONARNI_ID = "radar-icon-stacionarni"
private const val USER_ICON_ID = "user-icon"

@Composable
fun MapScreen(
    onOpenDrawer: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val coroutineScope = rememberCoroutineScope()

    val activeRadars by viewModel.activeRadars.collectAsState()
    val userLocation by viewModel.locationService.location.collectAsState()
    val userHeading by viewModel.locationService.heading.collectAsState()
    val isActiveTracking by viewModel.locationService.isActiveTracking.collectAsState()

    val mapViewRef = remember { MapView(context) }
    val alertService = viewModel.alertService

    val speedLimitInZone by alertService.speedLimit.collectAsState()
    val currentSpeed by viewModel.locationService.speedKmh.collectAsState()
    val isInRadarZone by alertService.isInZone.collectAsState()
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedRadar by viewModel.selectedRadar.collectAsState()
    var isMapReady by remember { mutableStateOf(false) }
    var userSymbol by remember { mutableStateOf<Symbol?>(null) }
    var didInitialZoom by remember { mutableStateOf(false) }
    var isTransitioningToTracking by remember { mutableStateOf(false) }
    var showNoGps by remember { mutableStateOf(false) }
    var locationFound by remember { mutableStateOf(false) }
    var gpsWasDisabled by remember { mutableStateOf(false) }

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
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                    as android.location.LocationManager
            val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

            if (!gpsEnabled) {
                showNoGps = true
                gpsWasDisabled = true
            }

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
        alertService.setActiveRadars(activeRadars)
    }

    LaunchedEffect(userLocation) {
        val loc = userLocation ?: return@LaunchedEffect
        if (isActiveTracking) {
            alertService.checkProximity(loc)
        }
    }

    LaunchedEffect(userLocation, userHeading, isMapReady, isActiveTracking) {
        val map = mapRef ?: return@LaunchedEffect
        val sm = symbolManager ?: return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect

        map.uiSettings.isScrollGesturesEnabled = !isActiveTracking
        map.uiSettings.isZoomGesturesEnabled = !isActiveTracking
        map.uiSettings.isRotateGesturesEnabled = !isActiveTracking
        map.uiSettings.isTiltGesturesEnabled = !isActiveTracking

        val existing = userSymbol
        val rotation =  0f

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
            locationFound = true
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
        } else if (isActiveTracking && !isTransitioningToTracking) {
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
                                viewModel.selectRadar(activeRadars.getOrNull(index))
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

            if (isActiveTracking) {
                SpeedOverlay(
                    isInRadarZone = isInRadarZone,
                    speedLimitInZone = speedLimitInZone,
                    currentSpeed = currentSpeed,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (didInitialZoom && locationFound) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                trackingButtonScale = 0.85f
                                delay(150)
                                trackingButtonScale = 1f

                                val map = mapRef ?: return@launch
                                if (isActiveTracking) {
                                    viewModel.locationService.stopActiveTracking()
                                    alertService.stopAlerts()
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
                                        alertService.checkProximity(loc)
                                        val currentLoc = viewModel.locationService.location.value
                                        isTransitioningToTracking = true
                                        map.animateCamera(
                                            CameraUpdateFactory.newCameraPosition(
                                                CameraPosition.Builder()
                                                    .target(currentLoc?.let {
                                                        LatLng(
                                                            it.latitude,
                                                            it.longitude
                                                        )
                                                    }
                                                        ?: map.cameraPosition.target)
                                                    .zoom(15.0)
                                                    .tilt(0.0)
                                                    .bearing(userHeading)
                                                    .build()
                                            ), 500,

                                            object :
                                                org.maplibre.android.maps.MapLibreMap.CancelableCallback {
                                                override fun onCancel() {
                                                    isTransitioningToTracking = false
                                                }

                                                override fun onFinish() {

                                                    isTransitioningToTracking = false
                                                }
                                            }
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
                } else if (gpsWasDisabled && !locationFound) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                                        as android.location.LocationManager
                                val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

                                if (!gpsEnabled) {
                                    showNoGps = true
                                } else {
                                    showNoGps = false
                                    val lastKnown = viewModel.locationService.getLastKnownLocation()
                                    if (lastKnown != null) {
                                        viewModel.locationService.setInitialLocation(lastKnown)
                                    }
                                    viewModel.locationService.startPassiveTracking()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF2196F3)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 15.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 140.dp)
                    ) {
                        Text(
                            text = "PRONAĐI ME",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }

                FilterButton(
                    text = "AKTIVNI",
                    isActive = selectedFilter == MapViewModel.RadarFilter.ACTIVE,
                    onClick = {
                        viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE)
                    }
                )
                FilterButton(
                    text = "DANAS",
                    isActive = selectedFilter == MapViewModel.RadarFilter.TODAY,
                    enabled = !isActiveTracking,
                    onClick = {
                        viewModel.setFilter(MapViewModel.RadarFilter.TODAY)
                    }
                )
            }

            selectedRadar?.let { radar ->
                RadarInfoCard(
                    radar = radar,
                    onDismiss = { viewModel.selectRadar(null) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            if (showNoGps) {
                NoConnectionDialog(
                    title = "GPS je isključen",
                    message = "Molimo uključite lokaciju kako bi aplikacija mogla raditi.",
                    onDismiss = { showNoGps = false }
                )
            }
        }
    }
}