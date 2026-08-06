package com.amko.roadflow.presentation.screens

import android.graphics.Color
import android.graphics.PointF
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult

import com.amko.roadflow.R
import com.amko.roadflow.data.local.RouteResult
import com.amko.roadflow.data.local.RoutingService
import com.amko.roadflow.data.local.Secrets.MAP_API_KEY
import com.amko.roadflow.presentation.components.*
import com.amko.roadflow.presentation.viewmodel.MapViewModel
import com.amko.roadflow.utils.createCircleFeature
import com.amko.roadflow.utils.createRadarBitmap
import com.amko.roadflow.utils.createUserBitmap
import com.amko.roadflow.utils.MapDebugLogger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.isActive

private const val RADAR_ICON_ID = "radar-icon"
private const val RADAR_ICON_STACIONARNI_ID = "radar-icon-stacionarni"
private const val USER_ICON_ID = "user-icon"
private const val DESTINATION_ICON_ID = "destination-icon"
private const val USER_SORT_KEY = 1000f
private const val RADAR_SORT_KEY = 0f
private const val DESTINATION_SORT_KEY = 2000f

private fun createDestinationBitmap(context: android.content.Context): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.parseColor("#E53935")
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 3.5f, paint)

    paint.color = android.graphics.Color.parseColor("#E53935")
    canvas.drawCircle(size / 2f, size / 2f, size / 7f, paint)

    return bitmap
}

private fun isSymbolValid(sm: SymbolManager, symbol: Symbol?): Boolean {
    if (symbol == null) return false
    return try {
        sm.annotations.get(symbol.id) != null
    } catch (e: Exception) {
        false
    }
}

@Composable
fun MapScreen(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val window = activity?.window

        if (window != null) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            if (window != null) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
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
    var symbolManagerGeneration by remember { mutableStateOf(0) }
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedRadar by viewModel.selectedRadar.collectAsState()
    var isMapReady by remember { mutableStateOf(false) }
    var mapInitialized by remember { mutableStateOf(false) }
    var userSymbol by remember { mutableStateOf<Symbol?>(null) }
    var destinationSymbol by remember { mutableStateOf<Symbol?>(null) }
    var markerAnimator by remember { mutableStateOf<android.animation.ValueAnimator?>(null) }
    var lastAnimatedLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var lastFixTimestamp by remember { mutableStateOf(0L) }
    val hadSavedCameraOnEnter = remember { viewModel.savedCameraLat != null }
    var didInitialZoom by remember { mutableStateOf(hadSavedCameraOnEnter) }
    var isTransitioningToTracking by remember { mutableStateOf(false) }
    var showNoGps by remember { mutableStateOf(false) }
    var locationFound by remember { mutableStateOf(hadSavedCameraOnEnter) }
    var gpsWasDisabled by remember { mutableStateOf(false) }
    var isGpsEnabled by remember { mutableStateOf(true) }
    var showGpsLoading by remember { mutableStateOf(false) }

    var currentRouteResult by remember { mutableStateOf<RouteResult?>(null) }
    var selectedDestination by remember { mutableStateOf<LatLng?>(null) }
    var destinationScreenPoint by remember { mutableStateOf<PointF?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    val routingService = remember { RoutingService() }

    fun updateDestinationScreenPoint() {
        val dest = selectedDestination
        val map = mapRef
        if (dest != null && map != null) {
            destinationScreenPoint = map.projection.toScreenLocation(dest)
        } else {
            destinationScreenPoint = null
        }
    }

    suspend fun createUserSymbolSafely(sm: SymbolManager, latLng: LatLng, rotate: Float): Symbol? {
        return try {
            val created = sm.create(
                SymbolOptions()
                    .withLatLng(latLng)
                    .withIconImage(USER_ICON_ID)
                    .withIconSize(1.2f)
                    .withIconRotate(rotate)
                    .withSymbolSortKey(USER_SORT_KEY)
            )
            MapDebugLogger.logMarkerAdded("UserMarker", created.id.toString(), latLng.latitude, latLng.longitude)
            created
        } catch (e: Exception) {
            MapDebugLogger.logMarkerError("UserMarker", "CreateFailed", e.message ?: "Unknown error")
            null
        }
    }

    suspend fun ensureUserSymbol(sm: SymbolManager, latLng: LatLng, rotate: Float): Symbol? {
        return viewModel.symbolMutex.withLock {
            val existing = userSymbol
            if (existing == null || !isSymbolValid(sm, existing)) {
                val created = createUserSymbolSafely(sm, latLng, rotate)
                userSymbol = created
                lastAnimatedLocation = null
                created
            } else {
                existing
            }
        }
    }

    LaunchedEffect(Unit) {
        MapDebugLogger.init(context)
        MapDebugLogger.log("MapScreen", "MapScreen initialized, tracking logs started")
        launch {
            delay(2 * 60 * 1000)
            MapDebugLogger.log("MapScreen", "2 minutes of map usage completed")
        }

        while (true) {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                    as android.location.LocationManager
            val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

            if (!gpsEnabled && isGpsEnabled) {
                MapDebugLogger.log("MapScreen", "GPS disabled detected")
                showNoGps = true
                gpsWasDisabled = true
                showGpsLoading = false

                if (isActiveTracking) {
                    MapDebugLogger.log("MapScreen", "Stopping active tracking due to no GPS")
                    viewModel.locationService.stopActiveTracking()
                    viewModel.stopBackgroundTracking()
                    alertService.stopAlerts()
                }

                viewModel.locationService.stopPassiveTracking()

                val sm = symbolManager
                viewModel.symbolMutex.withLock {
                    val existing = userSymbol
                    if (sm != null && existing != null) {
                        MapDebugLogger.logMarkerRemoved("UserMarker", existing.id.toString(), "No GPS signal")
                        try {
                            sm.delete(existing)
                        } catch (e: Exception) {
                            MapDebugLogger.logMarkerError("UserMarker", "DeleteOnGpsLost", e.message ?: "Unknown error")
                        }
                        userSymbol = null
                        lastAnimatedLocation = null
                    }
                }

                locationFound = false
                didInitialZoom = false
            } else if (gpsEnabled && !isGpsEnabled && gpsWasDisabled && !locationFound) {
                MapDebugLogger.log("MapScreen", "GPS re-enabled detected")
                showNoGps = false
                showGpsLoading = true

                val lastKnown = viewModel.locationService.getLastKnownLocation()
                if (lastKnown != null) {
                    viewModel.locationService.setInitialLocation(lastKnown)
                }
                viewModel.locationService.startPassiveTracking()
            }
            isGpsEnabled = gpsEnabled
            delay(1000)
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.locationService.startPassiveTracking()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val backgroundGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!backgroundGranted) {
                    backgroundPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
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

    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

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
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(symbolManagerGeneration) {
        if (symbolManagerGeneration == 0) return@LaunchedEffect
        val sm = symbolManager ?: return@LaunchedEffect
        MapDebugLogger.log("MapMarkers", "SymbolManager regenerated (gen=$symbolManagerGeneration), rebuilding all markers")

        viewModel.symbolMutex.withLock {
            markerAnimator?.cancel()
            userSymbol = null
            destinationSymbol = null
            lastAnimatedLocation = null

            val loc = userLocation
            if (loc != null) {
                userSymbol = createUserSymbolSafely(sm, LatLng(loc.latitude, loc.longitude), 0f)
            }

            val dest = selectedDestination
            if (dest != null) {
                try {
                    destinationSymbol = sm.create(
                        SymbolOptions()
                            .withLatLng(dest)
                            .withIconImage(DESTINATION_ICON_ID)
                            .withIconSize(1.0f)
                            .withSymbolSortKey(DESTINATION_SORT_KEY)
                    )
                } catch (e: Exception) {
                    MapDebugLogger.logMarkerError("DestinationMarker", "RecreateFailed", e.message ?: "Unknown error")
                }
            }
        }
    }

    LaunchedEffect(activeRadars, styleRef, symbolManagerGeneration) {
        val sm = symbolManager ?: return@LaunchedEffect
        styleRef ?: return@LaunchedEffect
        val requestRadars = activeRadars
        val requestId = System.currentTimeMillis()

        MapDebugLogger.log("MapMarkers", "[$requestId] START effect radars=${requestRadars.size}")

        val newSymbolsOptions = withContext(Dispatchers.Default) {
            requestRadars.mapIndexedNotNull { index, radar ->
                val lat = radar.latitude ?: return@mapIndexedNotNull null
                val lng = radar.longitude ?: return@mapIndexedNotNull null
                val iconId = if (radar.coordinate?.stacionaran == true)
                    RADAR_ICON_STACIONARNI_ID else RADAR_ICON_ID
                SymbolOptions()
                    .withLatLng(LatLng(lat, lng))
                    .withIconImage(iconId)
                    .withIconSize(1.0f)
                    .withSymbolSortKey(RADAR_SORT_KEY)
                    .withData(com.google.gson.JsonPrimitive(index))
            }
        }

        MapDebugLogger.log("MapMarkers", "[$requestId] built symbolOptions=${newSymbolsOptions.size} (dropped=${requestRadars.size - newSymbolsOptions.size})")

        val radius = context.getSharedPreferences("sound_settings", android.content.Context.MODE_PRIVATE)
            .getInt("alert_radius", 200).toDouble()
        val featureCollection = withContext(Dispatchers.Default) {
            val features = requestRadars.mapNotNull { radar ->
                val lat = radar.latitude ?: return@mapNotNull null
                val lng = radar.longitude ?: return@mapNotNull null
                createCircleFeature(lng, lat, radius)
            }
            FeatureCollection.fromFeatures(features)
        }

        if (!isActive) {
            MapDebugLogger.logMarkerError("RadarMarker", "CoroutineAborted", "Otkazano iscrtavanje radara prije primjene")
            return@LaunchedEffect
        }
        if (requestRadars !== activeRadars) {
            MapDebugLogger.logMarkerError("RadarMarker", "StaleData", "Podaci zastarjeli, prekid iscrtavanja (bilo: ${requestRadars.size}, sada: ${activeRadars.size})")
            return@LaunchedEffect
        }

        MapDebugLogger.logMutex("RadarEffect", "[$requestId] waiting for lock")
        var radarUpdateSucceeded = false
        var afterCount = -1
        viewModel.symbolMutex.withLock {
            MapDebugLogger.logMutex("RadarEffect", "[$requestId] lock acquired")
            val savedLatLng = userSymbol?.latLng
            val savedRotate = userSymbol?.iconRotate ?: 0f
            val savedDestLatLng = destinationSymbol?.latLng

            try {
                val beforeCount = sm.annotations.size()
                MapDebugLogger.logMarkerRemoved("RadarMarker", "ALL", "Brisanje svih markera sa mape, obrisano ukupno: $beforeCount")
                sm.deleteAll()
                userSymbol = null
                destinationSymbol = null

                if (newSymbolsOptions.isNotEmpty()) {
                    val created = sm.create(newSymbolsOptions)
                    MapDebugLogger.logMarkerUpdated("RadarMarker", "BATCH_CREATE", "Kreiranje", "Uspješno iscrtano ${created.size} radara")
                } else {
                    MapDebugLogger.logMarkerRemoved("RadarMarker", "BATCH_EMPTY", "Nijedan radar nije kreiran jer je lista prazna")
                }

                if (savedLatLng != null) {
                    userSymbol = createUserSymbolSafely(sm, savedLatLng, savedRotate)
                    lastAnimatedLocation = null
                    MapDebugLogger.log("MapMarkers", "[$requestId] restored userSymbol id=${userSymbol?.id}")
                } else {
                    MapDebugLogger.log("MapMarkers", "[$requestId] no previous userSymbol to restore")
                }

                if (savedDestLatLng != null) {
                    try {
                        destinationSymbol = sm.create(
                            SymbolOptions()
                                .withLatLng(savedDestLatLng)
                                .withIconImage(DESTINATION_ICON_ID)
                                .withIconSize(1.0f)
                                .withSymbolSortKey(DESTINATION_SORT_KEY)
                        )
                        MapDebugLogger.log("MapMarkers", "[$requestId] restored destinationSymbol id=${destinationSymbol?.id}")
                    } catch (e: Exception) {
                        MapDebugLogger.logMarkerError("DestinationMarker", "RestoreFailed", e.message ?: "Unknown error")
                    }
                }

                afterCount = sm.annotations.size()
                radarUpdateSucceeded = true
            } catch (e: Exception) {
                MapDebugLogger.logMarkerError("RadarMarker", "BatchUpdateFailed", e.message ?: "Unknown error")
                symbolManagerGeneration = symbolManagerGeneration + 1
            }
        }
        MapDebugLogger.logMutex("RadarEffect", "[$requestId] lock released")

        if (radarUpdateSucceeded) {
            mapRef?.style?.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(
                "radar-zones-source"
            )?.setGeoJson(featureCollection)

            alertService.setActiveRadars(requestRadars)

            val expectedCount = newSymbolsOptions.size +
                    (if (userSymbol != null) 1 else 0) +
                    (if (destinationSymbol != null) 1 else 0)
            MapDebugLogger.log("MapMarkers", "[$requestId] DONE afterCount=$afterCount expected=$expectedCount")

            if (afterCount != expectedCount) {
                MapDebugLogger.logMarkerError("RadarMarker", "CountMismatch", "Rendering greška! Očekivano $expectedCount markera, ali izbrojano $afterCount markera.")
            }
        } else {
            MapDebugLogger.log("MapMarkers", "[$requestId] SKIPPED geojson update because batch update failed")
        }
    }

    LaunchedEffect(userLocation) {
        val loc = userLocation ?: return@LaunchedEffect
        if (isActiveTracking) {
            alertService.checkProximity(loc)
        }
    }

    LaunchedEffect(locationFound, showGpsLoading) {
        if (showGpsLoading && locationFound) {
            showGpsLoading = false
        }
    }

    LaunchedEffect(currentRouteResult, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        val routeSource = style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("route-source") ?: return@LaunchedEffect

        val route = currentRouteResult
        if (route == null || route.coordinates.isEmpty()) {
            routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            val points = route.coordinates.map { Point.fromLngLat(it.second, it.first) }
            val lineString = LineString.fromLngLats(points)
            routeSource.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
        }
    }

    LaunchedEffect(selectedDestination, symbolManager, isMapReady, symbolManagerGeneration) {
        val sm = symbolManager ?: return@LaunchedEffect
        viewModel.symbolMutex.withLock {
            val existing = destinationSymbol
            if (existing != null && isSymbolValid(sm, existing)) {
                try {
                    sm.delete(existing)
                } catch (e: Exception) {
                    MapDebugLogger.logMarkerError("DestinationMarker", "DeleteFailed", e.message ?: "Unknown error")
                }
            }
            destinationSymbol = null

            val dest = selectedDestination
            if (dest != null) {
                try {
                    destinationSymbol = sm.create(
                        SymbolOptions()
                            .withLatLng(dest)
                            .withIconImage(DESTINATION_ICON_ID)
                            .withIconSize(1.0f)
                            .withSymbolSortKey(DESTINATION_SORT_KEY)
                    )
                } catch (e: Exception) {
                    MapDebugLogger.logMarkerError("DestinationMarker", "CreateFailed", e.message ?: "Unknown error")
                }
            }
        }
        updateDestinationScreenPoint()
    }

    LaunchedEffect(userLocation, userHeading, isMapReady, isActiveTracking, isGpsEnabled, symbolManagerGeneration) {
        val map = mapRef ?: return@LaunchedEffect
        val sm = symbolManager ?: return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect
        if (!isGpsEnabled) return@LaunchedEffect

        MapDebugLogger.log("UserMarker", "Location update received: lat=${loc.latitude} lng=${loc.longitude} isActiveTracking=$isActiveTracking")

        map.uiSettings.isScrollGesturesEnabled = !isActiveTracking
        map.uiSettings.isZoomGesturesEnabled = !isActiveTracking
        map.uiSettings.isRotateGesturesEnabled = !isActiveTracking
        map.uiSettings.isTiltGesturesEnabled = !isActiveTracking

        val rotation = 0f

        val currentSymbol = viewModel.symbolMutex.withLock { userSymbol }
        if (currentSymbol == null || !isSymbolValid(sm, currentSymbol)) {
            MapDebugLogger.log("UserMarker", "Creating initial user symbol")
            ensureUserSymbol(sm, LatLng(loc.latitude, loc.longitude), rotation)
        } else {
            val locChanged = lastAnimatedLocation == null ||
                    loc.latitude != lastAnimatedLocation?.latitude ||
                    loc.longitude != lastAnimatedLocation?.longitude

            if (!locChanged) {
                MapDebugLogger.logMarkerUpdated("UserMarker", currentSymbol.id.toString(), "Rotation", "Lat: ${loc.latitude} Lng: ${loc.longitude}")
                viewModel.symbolMutex.withLock {
                    val sym = userSymbol
                    if (sym != null) {
                        try {
                            sym.iconRotate = rotation
                            sm.update(sym)
                        } catch (e: Exception) {
                            MapDebugLogger.logMarkerError("UserMarker", "RotationUpdate", e.message ?: "Unknown error")
                            userSymbol = null
                            lastAnimatedLocation = null
                        }
                    }
                }
            } else {
                MapDebugLogger.logMarkerUpdated("UserMarker", currentSymbol.id.toString(), "Position", "Lat: ${loc.latitude} Lng: ${loc.longitude}")
                lastAnimatedLocation = loc
                val startLatLng = currentSymbol.latLng
                val targetLatLng = LatLng(loc.latitude, loc.longitude)
                val jumpDistanceMeters = startLatLng.distanceTo(targetLatLng)

                val now = android.os.SystemClock.elapsedRealtime()
                val elapsedSinceLastFix = if (lastFixTimestamp == 0L) 850L else (now - lastFixTimestamp)
                lastFixTimestamp = now

                markerAnimator?.cancel()

                if (jumpDistanceMeters > 300.0) {
                    MapDebugLogger.log("UserMarker", "Veliki skok lokacije (${jumpDistanceMeters}m), instant premještanje bez animacije")
                    viewModel.symbolMutex.withLock {
                        val sym = userSymbol
                        if (sym != null) {
                            try {
                                sym.latLng = targetLatLng
                                sym.iconRotate = rotation
                                sm.update(sym)
                            } catch (e: Exception) {
                                MapDebugLogger.logMarkerError("UserMarker", "InstantJumpUpdate", e.message ?: "Unknown error")
                                userSymbol = null
                                lastAnimatedLocation = null
                            }
                        }
                    }
                } else {
                    val animDuration = elapsedSinceLastFix.coerceIn(300L, 1500L)
                    markerAnimator = android.animation.ValueAnimator.ofObject(
                        com.amko.roadflow.utils.LatLngEvaluator(),
                        startLatLng,
                        targetLatLng
                    ).apply {
                        duration = animDuration
                        interpolator = android.view.animation.LinearInterpolator()
                        addUpdateListener { animator ->
                            val animatedLatLng = animator.animatedValue as LatLng
                            val sym = userSymbol
                            if (sym == null) {
                                cancel()
                                return@addUpdateListener
                            }
                            if (viewModel.symbolMutex.tryLock()) {
                                try {
                                    if (isSymbolValid(sm, sym)) {
                                        sym.latLng = animatedLatLng
                                        sym.iconRotate = rotation
                                        sm.update(sym)
                                    } else {
                                        throw IllegalStateException("Symbol no longer valid on SymbolManager")
                                    }
                                } catch (e: Exception) {
                                    MapDebugLogger.logMarkerError("UserMarker", "AnimationUpdate", e.message ?: "Unknown error")
                                    markerAnimator?.cancel()
                                    userSymbol = null
                                    lastAnimatedLocation = null
                                } finally {
                                    viewModel.symbolMutex.unlock()
                                }
                            }
                        }
                        start()
                    }
                }
            }
            if (!didInitialZoom) {
                MapDebugLogger.log("Camera", "Executing initial zoom")
                didInitialZoom = true
                locationFound = true
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(loc.latitude, loc.longitude))
                            .zoom(if (isActiveTracking) 17.0 else 14.0)
                            .tilt(if (isActiveTracking) 45.0 else 0.0)
                            .bearing(if (isActiveTracking) userHeading else 0.0)
                            .build()
                    ), 1000
                )
            } else if (isActiveTracking && !isTransitioningToTracking) {
            map.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(loc.latitude, loc.longitude))
                        .zoom(map.cameraPosition.zoom)
                        .tilt(45.0)
                        .bearing(userHeading)
                        .build()
                ), 300
            )
        }
    }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let {
                    if (isLandscape) it.padding(start = 88.dp) else it.padding(bottom = 80.dp)
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapViewRef },
                update = { view ->
                    if (mapInitialized) return@AndroidView
                    view.getMapAsync { map ->
                        if (mapInitialized) return@getMapAsync
                        mapInitialized = true
                        mapRef = map
                        map.uiSettings.isCompassEnabled = false
                        map.setMinZoomPreference(6.0)
                        map.setMaxZoomPreference(18.0)

                        val bihBounds = LatLngBounds.Builder()
                            .include(LatLng(45.17, 15.60))
                            .include(LatLng(42.56, 19.50))
                            .build()
                        map.setLatLngBoundsForCameraTarget(bihBounds)

                        map.addOnCameraMoveListener {
                            updateDestinationScreenPoint()
                        }

                        map.addOnCameraIdleListener {
                            updateDestinationScreenPoint()
                            if (!isMapReady) return@addOnCameraIdleListener
                            val pos = map.cameraPosition
                            val isStaleMap = map !== mapRef
                            if (isStaleMap) return@addOnCameraIdleListener
                            viewModel.saveCameraState(
                                lat = pos.target?.latitude ?: 0.0,
                                lng = pos.target?.longitude ?: 0.0,
                                zoom = pos.zoom,
                                tilt = pos.tilt,
                                bearing = pos.bearing
                            )
                        }

                        map.setStyle(MAP_API_KEY) { style ->
                            styleRef = style

                            style.addImage(RADAR_ICON_ID, createRadarBitmap(context, false))
                            style.addImage(RADAR_ICON_STACIONARNI_ID, createRadarBitmap(context, true))
                            style.addImage(USER_ICON_ID, createUserBitmap(context))
                            style.addImage(DESTINATION_ICON_ID, createDestinationBitmap(context))

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    "radar-zones-source",
                                    FeatureCollection.fromFeatures(emptyList())
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

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    "route-source",
                                    FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.LineLayer("route-layer", "route-source").apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#1E88E5")),
                                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(6, 4f),
                                                org.maplibre.android.style.expressions.Expression.stop(14, 7f),
                                                org.maplibre.android.style.expressions.Expression.stop(18, 12f)
                                            )
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.lineCap(
                                            org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                                            org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                                        )
                                    )
                                }
                            )

                            val sm = SymbolManager(view, map, style).also {
                                it.iconAllowOverlap = true
                                it.iconIgnorePlacement = true
                                it.textAllowOverlap = true
                                it.textIgnorePlacement = true
                                symbolManager = it
                                symbolManagerGeneration = symbolManagerGeneration + 1
                            }
                            sm.addClickListener { symbol ->
                                val data = symbol.data
                                val index = if (data != null && data.isJsonPrimitive && data.asJsonPrimitive.isNumber) {
                                    data.asInt
                                } else {
                                    null
                                }
                                if (index != null) {
                                    viewModel.selectRadar(activeRadars.getOrNull(index))
                                } else {
                                    viewModel.selectRadar(null)
                                }
                                true
                            }

                            map.addOnMapClickListener {
                                viewModel.selectRadar(null)
                                true
                            }

                            val savedLat = viewModel.savedCameraLat
                            val savedLng = viewModel.savedCameraLng

                            if (savedLat != null && savedLng != null) {
                                val savedZoom = viewModel.savedCameraZoom ?: 12.0
                                val savedTilt = viewModel.savedCameraTilt ?: 0.0
                                val savedBearing = viewModel.savedCameraBearing ?: 0.0

                                map.moveCamera(
                                    CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.Builder()
                                            .target(LatLng(savedLat, savedLng))
                                            .zoom(savedZoom)
                                            .tilt(savedTilt)
                                            .bearing(savedBearing)
                                            .build()
                                    )
                                )
                            } else {
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(44.15, 17.80), 6.0
                                    )
                                )
                            }

                            isMapReady = true
                            MapDebugLogger.log("MapScreen", "Map rendering and Style fully loaded")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = if (isLandscape) 20.dp else 50.dp,
                        start = if (isLandscape) 20.dp else 16.dp,
                        end = if (isActiveTracking) 90.dp else 16.dp
                    )
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActiveTracking) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        LocationSearchBar(
                            onLocationSelected = { latLng, _ ->
                                selectedDestination = latLng
                                currentRouteResult = null
                                mapRef?.animateCamera(
                                    CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.Builder()
                                            .target(latLng)
                                            .zoom(15.0)
                                            .build()
                                    ), 1000
                                )
                            }
                        )
                    }
                }

                if (currentRouteResult != null || isCalculatingRoute) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF004E5A)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCalculatingRoute) {
                                Text(
                                    text = "Izračunavam...",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else if (currentRouteResult != null) {
                                val route = currentRouteResult!!
                                val totalMinutes = (route.durationSeconds / 60).toInt()
                                val hours = totalMinutes / 60
                                val remainingMinutes = totalMinutes % 60

                                val timeFormatted = when {
                                    hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
                                    hours > 0 -> "${hours}h"
                                    else -> "${totalMinutes} min"
                                }

                                val km = String.format("%.1f km", route.distanceMeters / 1000.0)

                                Column {
                                    Text(
                                        text = "Ruta: $km",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Vrijeme: $timeFormatted",
                                        color = androidx.compose.ui.graphics.Color(0xFFD2F7FF),
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = {
                                        selectedDestination = null
                                        currentRouteResult = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Ukloni rutu",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isActiveTracking) {
                destinationScreenPoint?.let { point ->
                    val density = LocalContext.current.resources.displayMetrics.density
                    val xDp = (point.x / density).dp - 16.dp
                    val yDp = (point.y / density).dp - 48.dp

                    Box(modifier = Modifier.offset(x = xDp, y = yDp)) {
                        Surface(
                            onClick = {
                                selectedDestination = null
                                currentRouteResult = null
                                destinationScreenPoint = null
                            },
                            shape = CircleShape,
                            color = androidx.compose.ui.graphics.Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Otkaži destinaciju",
                                    tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isActiveTracking) {
                SpeedOverlay(
                    isInRadarZone = isInRadarZone,
                    speedLimitInZone = speedLimitInZone,
                    currentSpeed = currentSpeed,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = if (isLandscape) 20.dp else 50.dp,
                            end = 20.dp
                        )
                )
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    FilterButton(
                        text = "DANAS",
                        isActive = selectedFilter == MapViewModel.RadarFilter.TODAY,
                        enabled = !isActiveTracking,
                        onClick = { viewModel.setFilter(MapViewModel.RadarFilter.TODAY) }
                    )
                    FilterButton(
                        text = "AKTIVNI",
                        isActive = selectedFilter == MapViewModel.RadarFilter.ACTIVE,
                        onClick = { viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE) }
                    )

                    if (didInitialZoom && locationFound && isGpsEnabled) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    trackingButtonScale = 0.85f
                                    delay(150)
                                    trackingButtonScale = 1f

                                    val map = mapRef ?: return@launch
                                    if (isActiveTracking) {
                                        MapDebugLogger.log("MapScreen", "User manually stopped tracking")
                                        viewModel.locationService.stopActiveTracking()
                                        viewModel.stopBackgroundTracking()
                                        alertService.stopAlerts()

                                        delay(100)

                                        map.animateCamera(
                                            CameraUpdateFactory.newCameraPosition(
                                                CameraPosition.Builder()
                                                    .zoom(13.0)
                                                    .tilt(0.0)
                                                    .bearing(0.0)
                                                    .build()
                                            ), 800
                                        )

                                        delay(800)
                                        viewModel.locationService.startPassiveTracking()
                                    } else {
                                        MapDebugLogger.log("MapScreen", "User manually started tracking")
                                        val uLoc = userLocation
                                        val dest = selectedDestination
                                        if (dest != null && uLoc != null) {
                                            isCalculatingRoute = true
                                            val result = routingService.getRoute(
                                                uLoc.latitude,
                                                uLoc.longitude,
                                                dest.latitude,
                                                dest.longitude
                                            )
                                            currentRouteResult = result
                                            isCalculatingRoute = false

                                            if (result != null && result.coordinates.isNotEmpty()) {
                                                val boundsBuilder = LatLngBounds.Builder()
                                                boundsBuilder.include(LatLng(uLoc.latitude, uLoc.longitude))
                                                result.coordinates.forEach { (lat, lng) ->
                                                    boundsBuilder.include(LatLng(lat, lng))
                                                }
                                                mapRef?.animateCamera(
                                                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
                                                )
                                            }
                                        }

                                        viewModel.locationService.stopPassiveTracking()
                                        viewModel.locationService.startActiveTracking()
                                        viewModel.startBackgroundTracking()
                                        viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE)

                                        val loc = viewModel.locationService.location.value
                                        if (loc != null) {
                                            alertService.checkProximity(loc)
                                            val currentLoc = viewModel.locationService.location.value
                                            isTransitioningToTracking = true
                                            map.animateCamera(
                                                CameraUpdateFactory.newCameraPosition(
                                                    CameraPosition.Builder()
                                                        .target(currentLoc?.let { LatLng(it.latitude, it.longitude) } ?: map.cameraPosition.target)
                                                        .zoom(17.0)
                                                        .tilt(45.0)
                                                        .bearing(userHeading)
                                                        .build()
                                                ), 500,
                                                object : MapLibreMap.CancelableCallback {
                                                    override fun onCancel() { isTransitioningToTracking = false }
                                                    override fun onFinish() { isTransitioningToTracking = false }
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
                                    androidx.compose.ui.graphics.Color(0xFFD2F7FF)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isActiveTracking) R.drawable.ic_stop else R.drawable.ic_nav_arrow
                                    ),
                                    contentDescription = null,
                                    tint = if (isActiveTracking)
                                        androidx.compose.ui.graphics.Color.White
                                    else
                                        androidx.compose.ui.graphics.Color(0xFF004E5A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isActiveTracking) "ZAUSTAVI" else "KRENI",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActiveTracking)
                                        androidx.compose.ui.graphics.Color.White
                                    else
                                        androidx.compose.ui.graphics.Color(0xFF004E5A)
                                )
                            }
                        }
                    } else if (!isGpsEnabled || (gpsWasDisabled && !locationFound)) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                                            as android.location.LocationManager
                                    val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

                                    if (!gpsEnabled) {
                                        showNoGps = true
                                    } else {
                                        showNoGps = false
                                        showGpsLoading = true
                                        val lastKnown = viewModel.locationService.getLastKnownLocation()
                                        if (lastKnown != null) {
                                            viewModel.locationService.setInitialLocation(lastKnown)
                                        }
                                        viewModel.locationService.startPassiveTracking()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_locate),
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF004E5A),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (didInitialZoom && locationFound && isGpsEnabled) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    trackingButtonScale = 0.85f
                                    delay(150)
                                    trackingButtonScale = 1f

                                    val map = mapRef ?: return@launch
                                    if (isActiveTracking) {
                                        MapDebugLogger.log("MapScreen", "User manually stopped tracking")
                                        viewModel.locationService.stopActiveTracking()
                                        viewModel.stopBackgroundTracking()
                                        alertService.stopAlerts()

                                        delay(100)

                                        map.animateCamera(
                                            CameraUpdateFactory.newCameraPosition(
                                                CameraPosition.Builder()
                                                    .zoom(13.0)
                                                    .tilt(0.0)
                                                    .bearing(0.0)
                                                    .build()
                                            ), 800
                                        )

                                        delay(800)
                                        viewModel.locationService.startPassiveTracking()
                                    } else {
                                        MapDebugLogger.log("MapScreen", "User manually started tracking")
                                        val uLoc = userLocation
                                        val dest = selectedDestination
                                        if (dest != null && uLoc != null) {
                                            isCalculatingRoute = true
                                            val result = routingService.getRoute(
                                                uLoc.latitude,
                                                uLoc.longitude,
                                                dest.latitude,
                                                dest.longitude
                                            )
                                            currentRouteResult = result
                                            isCalculatingRoute = false

                                            if (result != null && result.coordinates.isNotEmpty()) {
                                                val boundsBuilder = LatLngBounds.Builder()
                                                boundsBuilder.include(LatLng(uLoc.latitude, uLoc.longitude))
                                                result.coordinates.forEach { (lat, lng) ->
                                                    boundsBuilder.include(LatLng(lat, lng))
                                                }
                                                mapRef?.animateCamera(
                                                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
                                                )
                                            }
                                        }

                                        viewModel.locationService.stopPassiveTracking()
                                        viewModel.locationService.startActiveTracking()
                                        viewModel.startBackgroundTracking()
                                        viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE)

                                        val loc = viewModel.locationService.location.value
                                        if (loc != null) {
                                            alertService.checkProximity(loc)
                                            val currentLoc = viewModel.locationService.location.value
                                            isTransitioningToTracking = true
                                            map.animateCamera(
                                                CameraUpdateFactory.newCameraPosition(
                                                    CameraPosition.Builder()
                                                        .target(currentLoc?.let { LatLng(it.latitude, it.longitude) } ?: map.cameraPosition.target)
                                                        .zoom(17.0)
                                                        .tilt(45.0)
                                                        .bearing(userHeading)
                                                        .build()
                                                ), 500,
                                                object : MapLibreMap.CancelableCallback {
                                                    override fun onCancel() { isTransitioningToTracking = false }
                                                    override fun onFinish() { isTransitioningToTracking = false }
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
                                    androidx.compose.ui.graphics.Color(0xFFD2F7FF)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isActiveTracking) R.drawable.ic_stop else R.drawable.ic_nav_arrow
                                    ),
                                    contentDescription = null,
                                    tint = if (isActiveTracking)
                                        androidx.compose.ui.graphics.Color.White
                                    else
                                        androidx.compose.ui.graphics.Color(0xFF004E5A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isActiveTracking) "ZAUSTAVI" else "KRENI",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActiveTracking)
                                        androidx.compose.ui.graphics.Color.White
                                    else
                                        androidx.compose.ui.graphics.Color(0xFF004E5A)
                                )
                            }
                        }
                    } else if (!isGpsEnabled || (gpsWasDisabled && !locationFound)) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                                            as android.location.LocationManager
                                    val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

                                    if (!gpsEnabled) {
                                        showNoGps = true
                                    } else {
                                        showNoGps = false
                                        showGpsLoading = true
                                        val lastKnown = viewModel.locationService.getLastKnownLocation()
                                        if (lastKnown != null) {
                                            viewModel.locationService.setInitialLocation(lastKnown)
                                        }
                                        viewModel.locationService.startPassiveTracking()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_locate),
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF004E5A),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    FilterButton(
                        text = "AKTIVNI",
                        isActive = selectedFilter == MapViewModel.RadarFilter.ACTIVE,
                        onClick = { viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE) }
                    )
                    FilterButton(
                        text = "DANAS",
                        isActive = selectedFilter == MapViewModel.RadarFilter.TODAY,
                        enabled = !isActiveTracking,
                        onClick = { viewModel.setFilter(MapViewModel.RadarFilter.TODAY) }
                    )
                }
            }

            Button(
                onClick = {
                    val result = MapDebugLogger.saveToDownloads(context)
                    if (result != null) {
                        android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Greška pri čuvanju loga!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = "SAČUVAJ LOG",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            selectedRadar?.let { radar ->
                RadarInfoCard(
                    radar = radar,
                    isVertical = isLandscape,
                    onDismiss = { viewModel.selectRadar(null) },
                    modifier = if (isLandscape) {
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    } else {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    }
                )
            }

            if (showNoGps) {
                NoConnectionDialog(
                    title = "GPS je isključen",
                    message = "Molimo uključite lokaciju kako bi aplikacija mogla raditi.",
                    onDismiss = { showNoGps = false }
                )
            }

            if (showGpsLoading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isLandscape) 20.dp else 50.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color(0xFF004E5A),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Učitavanje GPS-a ...",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        BottomNavBar(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            isVertical = isLandscape,
            modifier = Modifier.align(
                if (isLandscape) Alignment.CenterStart else Alignment.BottomCenter
            )
        )
    }
}