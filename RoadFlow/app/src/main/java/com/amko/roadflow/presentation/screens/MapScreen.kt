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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.onSizeChanged

import com.amko.roadflow.R
import com.amko.roadflow.data.local.ActiveTrackingAnimator
import com.amko.roadflow.data.local.RouteResult
import com.amko.roadflow.data.local.RoutingService
import com.amko.roadflow.data.local.Secrets.MAP_API_KEY
import com.amko.roadflow.presentation.components.*
import com.amko.roadflow.presentation.viewmodel.MapViewModel
import com.amko.roadflow.utils.createCircleFeature
import com.amko.roadflow.utils.createRadarBitmap
import com.amko.roadflow.utils.createUserBitmap
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val RADAR_ICON_ID = "radar-icon"
private const val RADAR_ICON_STACIONARNI_ID = "radar-icon-stacionarni"
private const val USER_ICON_ID = "user-icon"
private const val DESTINATION_ICON_ID = "destination-icon"

private const val RADAR_SOURCE_ID = "radar-markers-source"
private const val RADAR_LAYER_ID = "radar-markers-layer"
private const val USER_SOURCE_ID = "user-marker-source"
private const val USER_LAYER_ID = "user-marker-layer"
private const val DESTINATION_SOURCE_ID = "destination-marker-source"
private const val DESTINATION_LAYER_ID = "destination-marker-layer"
private const val ROUTE_ALT_SOURCE_ID = "route-alt-source"
private const val ROUTE_ALT_LAYER_ID = "route-alt-layer"
private const val ROUTE_ALT_HITAREA_LAYER_ID = "route-alt-hitarea-layer"
private const val ROUTE_LABEL_SOURCE_ID = "route-label-source"
private const val ROUTE_LABEL_LAYER_ID = "route-label-layer"
private const val ROUTE_ALT_LABEL_SOURCE_ID = "route-alt-label-source"
private const val ROUTE_ALT_LABEL_LAYER_ID = "route-alt-label-layer"

private const val MIN_GEOJSON_UPDATE_INTERVAL_NANOS = 16_000_000L
private const val SNAP_DISTANCE_METERS = 20.0
private const val SNAP_MAX_ANGLE_DIFF_DEGREES = 55.0
private const val SNAP_DISTANCE_HYSTERESIS_METERS = 30.0
private const val SNAP_ANGLE_HYSTERESIS_DEGREES = 70.0
private const val OFF_ROUTE_REROUTE_DELAY_NANOS = 5_000_000_000
private const val OFF_ROUTE_DISTANCE_METERS = 50.0
private const val REROUTE_COOLDOWN_NANOS = 10_000_000_000L

private fun createDestinationBitmap(context: android.content.Context): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.BLACK
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 3.5f, paint)

    paint.color = android.graphics.Color.BLACK
    canvas.drawCircle(size / 2f, size / 2f, size / 7f, paint)

    return bitmap
}

private fun createRouteLabelBitmap(
    context: android.content.Context,
    line1: String,
    line2: String,
    backgroundColor: String = "#0D47A1",
    textColor: Int = android.graphics.Color.WHITE
): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    textPaint.color = textColor
    textPaint.textSize = 20f * density
    textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    textPaint.textAlign = android.graphics.Paint.Align.CENTER

    val paddingH = 16f * density
    val paddingV = 10f * density
    val lineSpacing = 4f * density

    val width1 = textPaint.measureText(line1)
    val width2 = textPaint.measureText(line2)
    val textWidth = maxOf(width1, width2)
    val fontMetrics = textPaint.fontMetrics
    val lineHeight = fontMetrics.descent - fontMetrics.ascent

    val boxWidth = (textWidth + paddingH * 2).toInt()
    val boxHeight = (lineHeight * 2 + lineSpacing + paddingV * 2).toInt()

    val bitmap = android.graphics.Bitmap.createBitmap(boxWidth, boxHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    bgPaint.color = android.graphics.Color.parseColor(backgroundColor)
    val rect = android.graphics.RectF(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat())
    canvas.drawRoundRect(rect, 10f * density, 10f * density, bgPaint)

    val centerX = boxWidth / 2f
    val firstBaseline = paddingV - fontMetrics.ascent
    canvas.drawText(line1, centerX, firstBaseline, textPaint)
    canvas.drawText(line2, centerX, firstBaseline + lineHeight + lineSpacing, textPaint)

    return bitmap
}

private fun userFeature(lat: Double, lng: Double, rotation: Float, iconScale: Float): FeatureCollection {
    val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
    feature.addNumberProperty("rotation", rotation)
    feature.addNumberProperty("iconScale", iconScale)
    return FeatureCollection.fromFeature(feature)
}

private fun destinationFeature(latLng: LatLng?): FeatureCollection {
    if (latLng == null) return FeatureCollection.fromFeatures(emptyList())
    return FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude)))
}

private fun pointAtFraction(coordinates: List<Pair<Double, Double>>, fraction: Double): Pair<Double, Double>? {
    if (coordinates.isEmpty()) return null
    if (coordinates.size == 1) return coordinates[0]

    val clampedFraction = fraction.coerceIn(0.0, 1.0)

    val mPerLat = 111320.0
    fun mPerLng(atLat: Double) = 111320.0 * Math.cos(Math.toRadians(atLat))

    var totalLength = 0.0
    val segmentLengths = mutableListOf<Double>()
    for (i in 0 until coordinates.size - 1) {
        val (aLat, aLng) = coordinates[i]
        val (bLat, bLng) = coordinates[i + 1]
        val dx = (bLng - aLng) * mPerLng(aLat)
        val dy = (bLat - aLat) * mPerLat
        val len = Math.sqrt(dx * dx + dy * dy)
        segmentLengths.add(len)
        totalLength += len
    }

    if (totalLength <= 0.0) return coordinates[coordinates.size / 2]

    val targetLength = totalLength * clampedFraction
    var accumulated = 0.0
    for (i in segmentLengths.indices) {
        val segLen = segmentLengths[i]
        if (accumulated + segLen >= targetLength) {
            val (aLat, aLng) = coordinates[i]
            val (bLat, bLng) = coordinates[i + 1]
            val t = if (segLen > 0.0) (targetLength - accumulated) / segLen else 0.0
            val lat = aLat + (bLat - aLat) * t
            val lng = aLng + (bLng - aLng) * t
            return Pair(lat, lng)
        }
        accumulated += segLen
    }

    return coordinates.last()
}

private fun midPointOfRoute(coordinates: List<Pair<Double, Double>>): Pair<Double, Double>? {
    return pointAtFraction(coordinates, 0.5)
}

private data class LabelPlacement(val routeIndex: Int, val isSelected: Boolean, val fraction: Double)

private fun resolveLabelFractions(
    map: MapLibreMap,
    selectedIndex: Int,
    routes: List<RouteResult>,
    labelWidthPx: Float,
    labelHeightPx: Float,
    previousFractions: Map<Int, Double>
): Map<Int, Double> {
    val movableIndices = routes.indices.filter { it != selectedIndex && routes[it].coordinates.isNotEmpty() }
    val fractions = mutableMapOf<Int, Double>()

    if (routes.getOrNull(selectedIndex)?.coordinates?.isNotEmpty() == true) {
        fractions[selectedIndex] = 0.5
    }
    movableIndices.forEach { index ->
        fractions[index] = previousFractions[index] ?: 0.5
    }

    fun screenPointFor(index: Int, fraction: Double): PointF? {
        val route = routes.getOrNull(index) ?: return null
        val point = pointAtFraction(route.coordinates, fraction) ?: return null
        return map.projection.toScreenLocation(LatLng(point.first, point.second))
    }

    val minGapX = labelWidthPx * 0.9f
    val minGapY = labelHeightPx * 0.9f
    val stepSize = 0.02
    val iterations = 40

    repeat(iterations) {
        val allIndices = (movableIndices + listOfNotNull(fractions.keys.find { it == selectedIndex }))
        val screenPoints = allIndices.associateWith { idx -> screenPointFor(idx, fractions[idx] ?: 0.5) }

        val adjustments = mutableMapOf<Int, Double>()

        for (a in allIndices) {
            val pointA = screenPoints[a] ?: continue
            for (b in allIndices) {
                if (a == b) continue
                val pointB = screenPoints[b] ?: continue

                val dx = Math.abs(pointA.x - pointB.x)
                val dy = Math.abs(pointA.y - pointB.y)
                val overlapping = dx < minGapX && dy < minGapY
                if (!overlapping) continue

                if (a == selectedIndex) continue

                val routeA = routes[a]
                val currentFractionA = fractions[a] ?: 0.5
                val forwardPoint = screenPointFor(a, (currentFractionA + stepSize).coerceIn(0.0, 1.0))
                val backwardPoint = screenPointFor(a, (currentFractionA - stepSize).coerceIn(0.0, 1.0))

                val forwardDist = forwardPoint?.let { distanceBetween(it, pointB) } ?: -1f
                val backwardDist = backwardPoint?.let { distanceBetween(it, pointB) } ?: -1f

                val moveForward = forwardDist >= backwardDist
                val delta = if (moveForward) stepSize else -stepSize
                adjustments[a] = (adjustments[a] ?: 0.0) + delta
            }
        }

        if (adjustments.isEmpty()) return@repeat

        adjustments.forEach { (index, delta) ->
            val current = fractions[index] ?: 0.5
            fractions[index] = (current + delta).coerceIn(0.05, 0.95)
        }
    }

    return fractions
}
private fun distanceBetween(a: PointF, b: PointF): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

private data class SnapResult(val lat: Double, val lng: Double, val bearing: Float, val distanceMeters: Double, val isAccepted: Boolean)

private fun bearingBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val dLngRad = Math.toRadians(lng2 - lng1)
    val y = Math.sin(dLngRad) * Math.cos(lat2Rad)
    val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLngRad)
    val bearingRad = Math.atan2(y, x)
    return (Math.toDegrees(bearingRad) + 360.0) % 360.0
}

private fun angleDiff(a: Double, b: Double): Double {
    var diff = (a - b) % 360.0
    if (diff > 180.0) diff -= 360.0
    if (diff < -180.0) diff += 360.0
    return Math.abs(diff)
}

private fun metersPerDegreeLat(): Double = 111320.0
private fun metersPerDegreeLng(atLat: Double): Double = 111320.0 * Math.cos(Math.toRadians(atLat))

private fun snapToRoute(
    userLat: Double,
    userLng: Double,
    userBearing: Double,
    routeCoordinates: List<Pair<Double, Double>>,
    wasSnappedLastTime: Boolean
): SnapResult? {
    if (routeCoordinates.size < 2) return null

    val mPerLat = metersPerDegreeLat()
    val mPerLng = metersPerDegreeLng(userLat)
    val userX = userLng * mPerLng
    val userY = userLat * mPerLat

    var bestDistance = Double.MAX_VALUE
    var bestLat = 0.0
    var bestLng = 0.0
    var bestSegmentBearing = 0.0
    var found = false

    for (i in 0 until routeCoordinates.size - 1) {
        val (aLat, aLng) = routeCoordinates[i]
        val (bLat, bLng) = routeCoordinates[i + 1]

        val ax = aLng * mPerLng
        val ay = aLat * mPerLat
        val bx = bLng * mPerLng
        val by = bLat * mPerLat

        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy

        val t = if (lengthSquared > 0.0) {
            (((userX - ax) * dx + (userY - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        } else 0.0

        val projX = ax + t * dx
        val projY = ay + t * dy

        val distX = userX - projX
        val distY = userY - projY
        val distance = Math.sqrt(distX * distX + distY * distY)

        if (distance < bestDistance) {
            bestDistance = distance
            bestLng = projX / mPerLng
            bestLat = projY / mPerLat
            bestSegmentBearing = bearingBetween(aLat, aLng, bLat, bLng)
            found = true
        }
    }

    if (!found) return null

    val distanceThreshold = if (wasSnappedLastTime) SNAP_DISTANCE_HYSTERESIS_METERS else SNAP_DISTANCE_METERS
    if (bestDistance > distanceThreshold) {
        return SnapResult(bestLat, bestLng, bestSegmentBearing.toFloat(), bestDistance, isAccepted = false)
    }

    val angleThreshold = if (wasSnappedLastTime) SNAP_ANGLE_HYSTERESIS_DEGREES else SNAP_MAX_ANGLE_DIFF_DEGREES
    val diff = angleDiff(userBearing, bestSegmentBearing)
    if (diff > angleThreshold) {
        return SnapResult(bestLat, bestLng, bestSegmentBearing.toFloat(), bestDistance, isAccepted = false)
    }

    return SnapResult(bestLat, bestLng, bestSegmentBearing.toFloat(), bestDistance, isAccepted = true)
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
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedRadar by viewModel.selectedRadar.collectAsState()
    var isMapReady by remember { mutableStateOf(false) }
    var mapInitialized by remember { mutableStateOf(false) }

    val animator = remember { ActiveTrackingAnimator() }
    val renderedPos by animator.renderedPos.collectAsState()

    var lastUserGeoJsonUpdateNanos by remember { mutableStateOf(0L) }
    val hadSavedCameraOnEnter = remember { viewModel.savedCameraLat != null }
    var didInitialZoom by remember { mutableStateOf(hadSavedCameraOnEnter) }
    var isTransitioningToTracking by remember { mutableStateOf(false) }
    var isCameraLocked by remember { mutableStateOf(false) }
    var showNoGps by remember { mutableStateOf(false) }
    var locationFound by remember { mutableStateOf(hadSavedCameraOnEnter) }
    var gpsWasDisabled by remember { mutableStateOf(false) }
    var isGpsEnabled by remember { mutableStateOf(true) }
    var showGpsLoading by remember { mutableStateOf(false) }
    var lastSnapWasSuccessful by remember { mutableStateOf(false) }
    var lastSnapDistanceMeters by remember { mutableStateOf<Double?>(null) }
    var offRouteSinceNanos by remember { mutableStateOf<Long?>(null) }
    var lastRerouteNanos by remember { mutableStateOf(0L) }
    var isRerouting by remember { mutableStateOf(false) }

    var routeAlternatives by remember { mutableStateOf<List<RouteResult>>(emptyList()) }
    var selectedRouteIndex by remember { mutableStateOf(0) }
    val currentRouteResult: RouteResult? = routeAlternatives.getOrNull(selectedRouteIndex)
    var labelFractions by remember { mutableStateOf<Map<Int, Double>>(emptyMap()) }

    var selectedDestination by remember { mutableStateOf<LatLng?>(null) }
    var selectedDestinationName by remember { mutableStateOf<String?>(null) }
    var destinationScreenPoint by remember { mutableStateOf<PointF?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isPickingOnMap by remember { mutableStateOf(false) }
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

    fun clearRoute() {
        routeAlternatives = emptyList()
        selectedRouteIndex = 0
        selectedDestination = null
        selectedDestinationName = null
    }

    suspend fun computeRoutesTo(destination: LatLng) {
        val uLoc = userLocation ?: return
        isCalculatingRoute = true
        val results = routingService.getRoutes(
            uLoc.latitude,
            uLoc.longitude,
            destination.latitude,
            destination.longitude
        )
        routeAlternatives = results
        selectedRouteIndex = 0
        isCalculatingRoute = false

        if (results.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            boundsBuilder.include(LatLng(uLoc.latitude, uLoc.longitude))
            results.forEach { r ->
                r.coordinates.forEach { (lat, lng) -> boundsBuilder.include(LatLng(lat, lng)) }
            }
            mapRef?.animateCamera(
                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150)
            )
        }
    }

    fun pushUserGeoJson(lat: Double, lng: Double, rotation: Float, force: Boolean = false) {
        val style = styleRef ?: return
        val now = System.nanoTime()
        val throttled = !force && now - lastUserGeoJsonUpdateNanos < MIN_GEOJSON_UPDATE_INTERVAL_NANOS
        if (throttled) return
        lastUserGeoJsonUpdateNanos = now
        val iconScale = if (isActiveTracking) 1.8f else 1.4f
        style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(USER_SOURCE_ID)
            ?.setGeoJson(userFeature(lat, lng, rotation, iconScale))
    }

    fun recomputeLabelFractions(forceRelayout: Boolean = false) {
        val map = mapRef ?: return
        if (routeAlternatives.isEmpty()) {
            labelFractions = emptyMap()
            return
        }
        if (!forceRelayout && labelFractions.keys.containsAll(routeAlternatives.indices.filter { routeAlternatives[it].coordinates.isNotEmpty() })) {
            return
        }
        val densityLocal = context.resources.displayMetrics.density
        val approxLabelWidthPx = 110f * densityLocal
        val approxLabelHeightPx = 50f * densityLocal
        labelFractions = resolveLabelFractions(
            map,
            selectedRouteIndex,
            routeAlternatives,
            approxLabelWidthPx,
            approxLabelHeightPx,
            labelFractions
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                    as android.location.LocationManager
            val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

            if (!gpsEnabled && isGpsEnabled) {
                showNoGps = true
                gpsWasDisabled = true
                showGpsLoading = false

                if (isActiveTracking) {
                    viewModel.locationService.stopActiveTracking()
                    viewModel.stopBackgroundTracking()
                    alertService.stopAlerts()
                }

                viewModel.locationService.stopPassiveTracking()

                styleRef?.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(USER_SOURCE_ID)
                    ?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))

                locationFound = false
                didInitialZoom = false
            } else if (gpsEnabled && !isGpsEnabled && gpsWasDisabled && !locationFound) {
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

    val gpsSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            showNoGps = false
            showGpsLoading = true
            coroutineScope.launch {
                val lastKnown = viewModel.locationService.getLastKnownLocation()
                if (lastKnown != null) {
                    viewModel.locationService.setInitialLocation(lastKnown)
                }
                viewModel.locationService.startPassiveTracking()
            }
        } else {
            showNoGps = false
        }
    }

    fun requestEnableGps() {
        val client = LocationServices.getSettingsClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        val task = client.checkLocationSettings(settingsRequest)

        task.addOnSuccessListener {
            showNoGps = false
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    gpsSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: android.content.IntentSender.SendIntentException) {
                }
            }
        }
    }

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
    val density = LocalContext.current.resources.displayMetrics.density
    val screenHeightPx = LocalConfiguration.current.screenHeightDp * density
    val currentMapPadding = (screenHeightPx * 0.65).toDouble()

    LaunchedEffect(isLandscape, isActiveTracking, isMapReady) {
        val map = mapRef ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect
        if (!isActiveTracking) return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect

        val correctedPadding = doubleArrayOf(0.0, currentMapPadding, 0.0, 0.0)

        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(17.0)
                    .tilt(45.0)
                    .bearing(userHeading.toDouble())
                    .padding(correctedPadding)
                    .build()
            )
        )
    }

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

    LaunchedEffect(activeRadars, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        val requestRadars = activeRadars

        val radarFeatures = withContext(Dispatchers.Default) {
            requestRadars.mapIndexedNotNull { index, radar ->
                val lat = radar.latitude ?: return@mapIndexedNotNull null
                val lng = radar.longitude ?: return@mapIndexedNotNull null
                val iconId = if (radar.coordinate?.stacionaran == true)
                    RADAR_ICON_STACIONARNI_ID else RADAR_ICON_ID
                val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                feature.addStringProperty("iconId", iconId)
                feature.addNumberProperty("index", index)
                feature
            }
        }

        val radius = context.getSharedPreferences("sound_settings", android.content.Context.MODE_PRIVATE)
            .getInt("alert_radius", 200).toDouble()
        val zoneFeatureCollection = withContext(Dispatchers.Default) {
            val features = requestRadars.mapNotNull { radar ->
                val lat = radar.latitude ?: return@mapNotNull null
                val lng = radar.longitude ?: return@mapNotNull null
                createCircleFeature(lng, lat, radius)
            }
            FeatureCollection.fromFeatures(features)
        }

        if (!isActive) return@LaunchedEffect
        if (requestRadars !== activeRadars) return@LaunchedEffect

        try {
            style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(RADAR_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(radarFeatures))
            style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("radar-zones-source")
                ?.setGeoJson(zoneFeatureCollection)

            alertService.setActiveRadars(requestRadars)
        } catch (e: Exception) {}
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

    LaunchedEffect(routeAlternatives) {
        recomputeLabelFractions(forceRelayout = true)
    }

    LaunchedEffect(routeAlternatives, selectedRouteIndex, styleRef, isActiveTracking, labelFractions) {
        val style = styleRef ?: return@LaunchedEffect
        val altSource = style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(ROUTE_ALT_SOURCE_ID)
        val mainSource = style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("route-source")
        val labelSource = style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(ROUTE_LABEL_SOURCE_ID)
        val altLabelSource = style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(ROUTE_ALT_LABEL_SOURCE_ID)

        if (routeAlternatives.isEmpty()) {
            altSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            mainSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            labelSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            altLabelSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return@LaunchedEffect
        }

        val altFeatures = mutableListOf<Feature>()
        val altLabelFeatures = mutableListOf<Feature>()
        var selectedFeature: Feature? = null
        var labelFeature: Feature? = null

        fun formatDurationAndDistance(route: RouteResult): Pair<String, String> {
            val totalMinutes = (route.durationSeconds / 60).toInt()
            val hours = totalMinutes / 60
            val remainingMinutes = totalMinutes % 60
            val timeFormatted = when {
                hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
                hours > 0 -> "${hours}h"
                else -> "${totalMinutes} min"
            }
            val km = String.format("%.0f km", route.distanceMeters / 1000.0)
            return Pair(timeFormatted, km)
        }

        routeAlternatives.forEachIndexed { index, route ->
            if (route.coordinates.isEmpty()) return@forEachIndexed
            val points = route.coordinates.map { Point.fromLngLat(it.second, it.first) }
            val lineString = LineString.fromLngLats(points)
            val feature = Feature.fromGeometry(lineString)
            feature.addBooleanProperty("isSelected", index == selectedRouteIndex)
            feature.addNumberProperty("routeIndex", index)

            val fraction = labelFractions[index] ?: 0.5
            val mid = pointAtFraction(route.coordinates, fraction)

            if (index == selectedRouteIndex) {
                selectedFeature = feature

                if (mid != null) {
                    val (timeFormatted, km) = formatDurationAndDistance(route)
                    val iconId = "route-label-icon-$index"
                    style.addImage(iconId, createRouteLabelBitmap(context, timeFormatted, km))

                    val labelPoint = Feature.fromGeometry(Point.fromLngLat(mid.second, mid.first))
                    labelPoint.addStringProperty("iconId", iconId)
                    labelFeature = labelPoint
                }
            } else {
                altFeatures.add(feature)

                if (mid != null) {
                    val (timeFormatted, km) = formatDurationAndDistance(route)
                    val iconId = "route-alt-label-icon-$index"
                    style.addImage(
                        iconId,
                        createRouteLabelBitmap(
                            context,
                            timeFormatted,
                            km,
                            backgroundColor = "#78909C",
                            textColor = android.graphics.Color.parseColor("#F5F5F5")
                        )
                    )

                    val altLabelPoint = Feature.fromGeometry(Point.fromLngLat(mid.second, mid.first))
                    altLabelPoint.addStringProperty("iconId", iconId)
                    altLabelPoint.addNumberProperty("routeIndex", index)
                    altLabelFeatures.add(altLabelPoint)
                }
            }
        }

        altSource?.setGeoJson(
            if (isActiveTracking) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeatures(altFeatures)
        )
        mainSource?.setGeoJson(
            if (selectedFeature != null) FeatureCollection.fromFeature(selectedFeature!!)
            else FeatureCollection.fromFeatures(emptyList())
        )
        labelSource?.setGeoJson(
            if (!isActiveTracking && labelFeature != null) FeatureCollection.fromFeature(labelFeature!!)
            else FeatureCollection.fromFeatures(emptyList())
        )
        altLabelSource?.setGeoJson(
            if (isActiveTracking) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeatures(altLabelFeatures)
        )
    }
    LaunchedEffect(selectedDestination, styleRef, isMapReady) {
        val style = styleRef ?: return@LaunchedEffect
        style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(DESTINATION_SOURCE_ID)
            ?.setGeoJson(destinationFeature(selectedDestination))
        updateDestinationScreenPoint()
    }

    LaunchedEffect(isActiveTracking, isGpsEnabled) {
        if (isActiveTracking && isGpsEnabled) {
            animator.start()
        }
    }

    LaunchedEffect(renderedPos) {
        val map = mapRef ?: return@LaunchedEffect
        if (!isMapReady || !isActiveTracking || !didInitialZoom || isTransitioningToTracking) return@LaunchedEffect

        pushUserGeoJson(renderedPos.lat, renderedPos.lng, renderedPos.bearing, force = true)

        val trackingPadding = doubleArrayOf(0.0, currentMapPadding, 0.0, 0.0)
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(renderedPos.lat, renderedPos.lng))
                    .zoom(17.0)
                    .tilt(45.0)
                    .bearing(renderedPos.bearing.toDouble())
                    .padding(trackingPadding)
                    .build()
            )
        )
    }

    LaunchedEffect(userLocation, userHeading, isMapReady, isActiveTracking, isGpsEnabled, isCameraLocked) {
        val map = mapRef ?: return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect
        if (!isGpsEnabled) return@LaunchedEffect

        val gesturesAllowed = !isActiveTracking && !isCameraLocked
        map.uiSettings.isScrollGesturesEnabled = gesturesAllowed
        map.uiSettings.isZoomGesturesEnabled = gesturesAllowed
        map.uiSettings.isRotateGesturesEnabled = gesturesAllowed
        map.uiSettings.isTiltGesturesEnabled = gesturesAllowed

        val activeRouteCoords = currentRouteResult?.coordinates
        val snapResult = if (isActiveTracking && activeRouteCoords != null) {
            snapToRoute(loc.latitude, loc.longitude, userHeading, activeRouteCoords, lastSnapWasSuccessful)
        } else {
            null
        }

        lastSnapWasSuccessful = snapResult?.isAccepted == true
        lastSnapDistanceMeters = snapResult?.distanceMeters

        val effectiveLat = if (snapResult?.isAccepted == true) snapResult.lat else loc.latitude
        val effectiveLng = if (snapResult?.isAccepted == true) snapResult.lng else loc.longitude

        val targetRotation = if (isActiveTracking) {
            if (snapResult?.isAccepted == true) snapResult.bearing else userHeading.toFloat()
        } else {
            0f
        }

        val trackingPadding = if (isActiveTracking) doubleArrayOf(0.0, currentMapPadding, 0.0, 0.0) else doubleArrayOf(0.0, 0.0, 0.0, 0.0)

        if (!didInitialZoom) {
            didInitialZoom = true
            locationFound = true
            animator.updateFix(effectiveLat, effectiveLng, targetRotation, currentSpeed, forceSnap = true)
            pushUserGeoJson(effectiveLat, effectiveLng, targetRotation, force = true)
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(effectiveLat, effectiveLng))
                        .zoom(if (isActiveTracking) 17.0 else 14.0)
                        .tilt(if (isActiveTracking) 45.0 else 0.0)
                        .bearing(if (isActiveTracking) userHeading else 0.0)
                        .padding(trackingPadding)
                        .build()
                ), 1000
            )
        } else if (!isActiveTracking) {
            animator.updateFix(effectiveLat, effectiveLng, targetRotation, currentSpeed, forceSnap = true)
            pushUserGeoJson(effectiveLat, effectiveLng, targetRotation, force = true)
        } else {
            val startLatLng = LatLng(animator.renderedPos.value.lat, animator.renderedPos.value.lng)
            val targetLatLng = LatLng(loc.latitude, loc.longitude)
            val jumpDistanceMeters = startLatLng.distanceTo(targetLatLng)

            if (jumpDistanceMeters > 300.0 || isTransitioningToTracking) {
                animator.updateFix(effectiveLat, effectiveLng, targetRotation, currentSpeed, forceSnap = true)
                pushUserGeoJson(effectiveLat, effectiveLng, targetRotation, force = true)
                if (!isTransitioningToTracking) {
                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(effectiveLat, effectiveLng))
                                .zoom(17.0)
                                .tilt(45.0)
                                .bearing(targetRotation.toDouble())
                                .padding(trackingPadding)
                                .build()
                        )
                    )
                }
            } else {
                animator.updateFix(effectiveLat, effectiveLng, targetRotation, currentSpeed, forceSnap = false)
            }
        }
    }

    LaunchedEffect(lastSnapWasSuccessful, lastSnapDistanceMeters, isActiveTracking) {
        if (!isActiveTracking) {
            offRouteSinceNanos = null
            return@LaunchedEffect
        }

        val distance = lastSnapDistanceMeters
        val isOffRoute = !lastSnapWasSuccessful && distance != null && distance > OFF_ROUTE_DISTANCE_METERS

        if (!isOffRoute) {
            offRouteSinceNanos = null
            return@LaunchedEffect
        }

        if (offRouteSinceNanos == null) {
            offRouteSinceNanos = System.nanoTime()
        }

        delay(OFF_ROUTE_REROUTE_DELAY_NANOS / 1_000_000L)

        val startedAt = offRouteSinceNanos ?: return@LaunchedEffect
        val elapsed = System.nanoTime() - startedAt
        if (elapsed < OFF_ROUTE_REROUTE_DELAY_NANOS) return@LaunchedEffect

        val now = System.nanoTime()
        if (now - lastRerouteNanos < REROUTE_COOLDOWN_NANOS) return@LaunchedEffect
        if (isRerouting) return@LaunchedEffect

        val destination = selectedDestination ?: return@LaunchedEffect
        val currentLoc = userLocation ?: return@LaunchedEffect

        isRerouting = true
        lastRerouteNanos = now
        try {
            val results = routingService.getRoutes(
                currentLoc.latitude,
                currentLoc.longitude,
                destination.latitude,
                destination.longitude
            )
            if (results.isNotEmpty()) {
                routeAlternatives = results
                selectedRouteIndex = 0
                labelFractions = emptyMap()
            }
        } catch (e: Exception) {
        } finally {
            isRerouting = false
            offRouteSinceNanos = null
        }
    }

    var bottomNavSizePx by remember { mutableStateOf(0) }
    val bottomNavSizeDp = with(androidx.compose.ui.platform.LocalDensity.current) { bottomNavSizePx.toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let {
                    if (isLandscape) it.padding(start = bottomNavSizeDp) else it.padding(bottom = bottomNavSizeDp)
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
                            recomputeLabelFractions()
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
                                    RADAR_SOURCE_ID,
                                    FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.SymbolLayer(RADAR_LAYER_ID, RADAR_SOURCE_ID).apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.iconImage(
                                            org.maplibre.android.style.expressions.Expression.get("iconId")
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconSize(1.0f),
                                        org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                                        org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true)
                                    )
                                }
                            )

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    ROUTE_ALT_SOURCE_ID,
                                    FeatureCollection.fromFeatures(emptyList())
                                )
                            )
                            style.addLayer(
                                org.maplibre.android.style.layers.LineLayer("route-alt-border-layer", ROUTE_ALT_SOURCE_ID).apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#0D47A1")),
                                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(6, 5f),
                                                org.maplibre.android.style.expressions.Expression.stop(14, 8f),
                                                org.maplibre.android.style.expressions.Expression.stop(18, 13f)
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
                            style.addLayer(
                                org.maplibre.android.style.layers.LineLayer(ROUTE_ALT_LAYER_ID, ROUTE_ALT_SOURCE_ID).apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#D6E4E8")),
                                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(6, 3f),
                                                org.maplibre.android.style.expressions.Expression.stop(14, 5f),
                                                org.maplibre.android.style.expressions.Expression.stop(18, 9f)
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

                            style.addLayer(
                                org.maplibre.android.style.layers.LineLayer(ROUTE_ALT_HITAREA_LAYER_ID, ROUTE_ALT_SOURCE_ID).apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#000000")),
                                        org.maplibre.android.style.layers.PropertyFactory.lineOpacity(0.001f),
                                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(6, 24f),
                                                org.maplibre.android.style.expressions.Expression.stop(14, 32f),
                                                org.maplibre.android.style.expressions.Expression.stop(18, 44f)
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

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    "route-source",
                                    FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.LineLayer("route-border-layer", "route-source").apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#081B33")),
                                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(6, 6f),
                                                org.maplibre.android.style.expressions.Expression.stop(14, 10f),
                                                org.maplibre.android.style.expressions.Expression.stop(18, 16f)
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

                            style.addLayer(
                                org.maplibre.android.style.layers.LineLayer("route-layer", "route-source").apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#0D47A1")),
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

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    ROUTE_LABEL_SOURCE_ID,
                                    FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.SymbolLayer(ROUTE_LABEL_LAYER_ID, ROUTE_LABEL_SOURCE_ID).apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.iconImage(
                                            org.maplibre.android.style.expressions.Expression.get("iconId")
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconSize(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(6, 0.5f),
                                                org.maplibre.android.style.expressions.Expression.stop(10, 0.65f),
                                                org.maplibre.android.style.expressions.Expression.stop(14, 0.8f),
                                                org.maplibre.android.style.expressions.Expression.stop(18, 1.0f)
                                            )
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconOffset(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(
                                                    6,
                                                    org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -10f))
                                                ),
                                                org.maplibre.android.style.expressions.Expression.stop(
                                                    10,
                                                    org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -13f))
                                                ),
                                                org.maplibre.android.style.expressions.Expression.stop(
                                                    14,
                                                    org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -16f))
                                                ),
                                                org.maplibre.android.style.expressions.Expression.stop(
                                                    18,
                                                    org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -18f))
                                                )
                                            )
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                                        org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true),
                                        org.maplibre.android.style.layers.PropertyFactory.iconAnchor(
                                            org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
                                        )
                                    )
                                }
                            )

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    ROUTE_ALT_LABEL_SOURCE_ID,
                                    FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.SymbolLayer(ROUTE_ALT_LABEL_LAYER_ID, ROUTE_ALT_LABEL_SOURCE_ID).apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.iconImage(
                                            org.maplibre.android.style.expressions.Expression.get("iconId")
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconSize(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(6, 0.5f),
                                                org.maplibre.android.style.expressions.Expression.stop(10, 0.65f),
                                                org.maplibre.android.style.expressions.Expression.stop(14, 0.8f),
                                                org.maplibre.android.style.expressions.Expression.stop(18, 1.0f)
                                            )
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconOffset(
                                            org.maplibre.android.style.expressions.Expression.interpolate(
                                                org.maplibre.android.style.expressions.Expression.linear(),
                                                org.maplibre.android.style.expressions.Expression.zoom(),
                                                org.maplibre.android.style.expressions.Expression.stop(
                                                    6,
                                                    org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -10f))
                                                ),
                                                org.maplibre.android.style.expressions.Expression.stop(
                                                    10,
                                                    org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -13f))
                                                ),
                                                org.maplibre.android.style.expressions.Expression.stop(
                                                    14,
                                                    org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -16f))
                                                ),
                                                org.maplibre.android.style.expressions.Expression.stop(
                                                    18,
                                                    org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -18f))
                                                )
                                            )
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                                        org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true),
                                        org.maplibre.android.style.layers.PropertyFactory.iconAnchor(
                                            org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
                                        )
                                    )
                                }
                            )

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    DESTINATION_SOURCE_ID,
                                    FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.SymbolLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID).apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.iconImage(DESTINATION_ICON_ID),
                                        org.maplibre.android.style.layers.PropertyFactory.iconSize(1.0f),
                                        org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                                        org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true)
                                    )
                                }
                            )

                            style.addSource(
                                org.maplibre.android.style.sources.GeoJsonSource(
                                    USER_SOURCE_ID,
                                    FeatureCollection.fromFeatures(emptyList())
                                )
                            )

                            style.addLayer(
                                org.maplibre.android.style.layers.SymbolLayer(USER_LAYER_ID, USER_SOURCE_ID).apply {
                                    setProperties(
                                        org.maplibre.android.style.layers.PropertyFactory.iconImage(USER_ICON_ID),
                                        org.maplibre.android.style.layers.PropertyFactory.iconSize(
                                            org.maplibre.android.style.expressions.Expression.get("iconScale")
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconRotate(
                                            org.maplibre.android.style.expressions.Expression.get("rotation")
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment(
                                            org.maplibre.android.style.layers.Property.ICON_ROTATION_ALIGNMENT_MAP
                                        ),
                                        org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                                        org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true)
                                    )
                                }
                            )
                            map.addOnMapClickListener { point ->
                                val screenPoint = map.projection.toScreenLocation(point)

                                val altLabelFeatures = map.queryRenderedFeatures(screenPoint, ROUTE_ALT_LABEL_LAYER_ID)
                                if (altLabelFeatures.isNotEmpty()) {
                                    val labelIdx = altLabelFeatures[0].getProperty("routeIndex")?.asInt
                                    if (labelIdx != null) {
                                        selectedRouteIndex = labelIdx
                                        return@addOnMapClickListener true
                                    }
                                }

                                val altFeatures = map.queryRenderedFeatures(screenPoint, ROUTE_ALT_HITAREA_LAYER_ID)
                                if (altFeatures.isNotEmpty()) {
                                    val idx = altFeatures[0].getProperty("routeIndex")?.asInt
                                    if (idx != null) {
                                        selectedRouteIndex = idx
                                        return@addOnMapClickListener true
                                    }
                                }

                                val features = map.queryRenderedFeatures(screenPoint, RADAR_LAYER_ID)
                                if (features.isNotEmpty()) {
                                    val index = features[0].getProperty("index")?.asInt
                                    if (index != null) {
                                        viewModel.selectRadar(activeRadars.getOrNull(index))
                                    } else {
                                        viewModel.selectRadar(null)
                                    }
                                } else {
                                    viewModel.selectRadar(null)
                                }
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
                        }
                    }
                }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = if (isLandscape) 20.dp else 50.dp,
                        start = if (isLandscape) 20.dp else 16.dp,
                        end = if (isActiveTracking) 90.dp else 16.dp
                    )
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isActiveTracking && !isCalculatingRoute) {
                    LocationSearchBar(
                        selectedLocationName = selectedDestinationName,
                        isPickingOnMap = isPickingOnMap,
                        onExpandedChange = { expanded ->
                            isSearchExpanded = expanded
                        },
                        onLocationSelected = { latLng, name ->
                            selectedDestination = latLng
                            selectedDestinationName = name
                            routeAlternatives = emptyList()
                            selectedRouteIndex = 0
                            coroutineScope.launch {
                                computeRoutesTo(latLng)
                            }
                        },
                        onLocationCleared = {
                            clearRoute()
                        },
                        onPickOnMapStart = {
                            isPickingOnMap = true
                            isSearchExpanded = false
                        },
                        onPickOnMapCancel = {
                            isPickingOnMap = false
                        },
                        onPickOnMapConfirm = {
                            val map = mapRef
                            val target = map?.cameraPosition?.target
                            if (target != null) {
                                selectedDestination = target
                                selectedDestinationName = "Odabrana lokacija"
                                routeAlternatives = emptyList()
                                selectedRouteIndex = 0
                                isPickingOnMap = false
                                coroutineScope.launch {
                                    computeRoutesTo(target)
                                }
                            } else {
                                isPickingOnMap = false
                            }
                        }
                    )
                }
            }

            if (isPickingOnMap) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_locate),
                    contentDescription = "Odabrana tačka na karti",
                    tint = androidx.compose.ui.graphics.Color(0xFFF44336),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 40.dp)
                        .size(40.dp)
                )
            }
            if (isActiveTracking && (currentRouteResult != null || isCalculatingRoute) && !isSearchExpanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = 20.dp,
                            start = if (isLandscape) (if (isActiveTracking) 40.dp else 160.dp) else 16.dp,
                            end = if (isLandscape) (if (isActiveTracking) 180.dp else 160.dp) else 16.dp
                        )
                        .fillMaxWidth()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4D7079)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCalculatingRoute) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Izračunavam...",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isLandscape) 20.sp else 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(if (isLandscape) 22.dp else 18.dp),
                                        color = androidx.compose.ui.graphics.Color.White,
                                        strokeWidth = 2.dp
                                    )
                                }
                            } else if (currentRouteResult != null) {
                                val route = currentRouteResult
                                val totalMinutes = (route.durationSeconds / 60).toInt()
                                val hours = totalMinutes / 60
                                val remainingMinutes = totalMinutes % 60

                                val timeFormatted = when {
                                    hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
                                    hours > 0 -> "${hours}h"
                                    else -> "${totalMinutes} min"
                                }

                                val km = String.format("%.0f km", route.distanceMeters / 1000.0)
                                val eta = java.time.LocalTime.now().plusSeconds(route.durationSeconds.toLong())
                                val etaFormatted = eta.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                                val infoFontSize = if (isLandscape) 20.sp else 16.sp

                                Text(
                                    text = km,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = infoFontSize,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = timeFormatted,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = infoFontSize,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$etaFormatted",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = infoFontSize,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { clearRoute() },
                                    modifier = Modifier.size(if (isLandscape) 32.dp else 28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Ukloni rutu",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(if (isLandscape) 22.dp else 18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }


            if (isActiveTracking) {
                val hasActiveRoute = currentRouteResult != null
                SpeedOverlay(
                    isInRadarZone = isInRadarZone,
                    speedLimitInZone = speedLimitInZone,
                    currentSpeed = currentSpeed,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = when {
                                isLandscape -> 20.dp
                                hasActiveRoute -> 130.dp
                                else -> 50.dp
                            },
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
                    if (!isActiveTracking) {
                        FilterButton(
                            text = "DANAS",
                            isActive = selectedFilter == MapViewModel.RadarFilter.TODAY,
                            onClick = { viewModel.setFilter(MapViewModel.RadarFilter.TODAY) }
                        )
                        FilterButton(
                            text = "AKTIVNI",
                            isActive = selectedFilter == MapViewModel.RadarFilter.ACTIVE,
                            onClick = { viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE) }
                        )

                        FilterButton(
                            text = "SVI",
                            isActive = selectedFilter == MapViewModel.RadarFilter.ALL,
                            onClick = { viewModel.setFilter(MapViewModel.RadarFilter.ALL) }
                        )
                    }

                    if (didInitialZoom && locationFound && isGpsEnabled) {
                        Button(
                            onClick = {
                                if (isPickingOnMap) return@Button
                                coroutineScope.launch {
                                    trackingButtonScale = 0.85f
                                    delay(150)
                                    trackingButtonScale = 1f

                                    val map = mapRef ?: return@launch
                                    if (isActiveTracking) {
                                        isCameraLocked = true
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
                                                    .padding(doubleArrayOf(0.0, 0.0, 0.0, 0.0))
                                                    .build()
                                            ), 800
                                        )

                                        delay(800)
                                        isCameraLocked = false
                                        viewModel.locationService.startPassiveTracking()
                                    } else {
                                        viewModel.locationService.stopPassiveTracking()
                                        viewModel.locationService.startActiveTracking()
                                        viewModel.startBackgroundTracking()
                                        viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE)

                                        val loc = viewModel.locationService.location.value
                                        if (loc != null) {
                                            alertService.checkProximity(loc)
                                            val currentLoc = viewModel.locationService.location.value
                                            isTransitioningToTracking = true
                                            isCameraLocked = true
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
                                                    override fun onCancel() { isTransitioningToTracking = false; isCameraLocked = false }
                                                    override fun onFinish() { isTransitioningToTracking = false; isCameraLocked = false }
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = !isPickingOnMap,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActiveTracking)
                                    androidx.compose.ui.graphics.Color(0xFFF44336)
                                else
                                    androidx.compose.ui.graphics.Color(0xFFD2F7FF),
                                disabledContainerColor = androidx.compose.ui.graphics.Color(0xFFD2F7FF).copy(alpha = 0.4f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                            contentPadding = PaddingValues(horizontal = 30.dp, vertical = 15.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 140.dp)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    alpha = if (isPickingOnMap) 0.5f else 1f
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
                                if (isPickingOnMap) return@Button
                                coroutineScope.launch {
                                    trackingButtonScale = 0.85f
                                    delay(150)
                                    trackingButtonScale = 1f

                                    val map = mapRef ?: return@launch
                                    if (isActiveTracking) {
                                        isCameraLocked = true
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
                                                    .padding(doubleArrayOf(0.0, 0.0, 0.0, 0.0))
                                                    .build()
                                            ), 800
                                        )

                                        delay(800)
                                        isCameraLocked = false
                                        viewModel.locationService.startPassiveTracking()
                                    } else {
                                        viewModel.locationService.stopPassiveTracking()
                                        viewModel.locationService.startActiveTracking()
                                        viewModel.startBackgroundTracking()
                                        viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE)

                                        val loc = viewModel.locationService.location.value
                                        if (loc != null) {
                                            alertService.checkProximity(loc)
                                            val currentLoc = viewModel.locationService.location.value
                                            isTransitioningToTracking = true
                                            isCameraLocked = true
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
                                                    override fun onCancel() { isTransitioningToTracking = false; isCameraLocked = false }
                                                    override fun onFinish() { isTransitioningToTracking = false; isCameraLocked = false }
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = !isPickingOnMap,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActiveTracking)
                                    androidx.compose.ui.graphics.Color(0xFFF44336)
                                else
                                    androidx.compose.ui.graphics.Color(0xFFD2F7FF),
                                disabledContainerColor = androidx.compose.ui.graphics.Color(0xFFD2F7FF).copy(alpha = 0.4f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                            contentPadding = PaddingValues(horizontal = 30.dp, vertical = 15.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 140.dp)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    alpha = if (isPickingOnMap) 0.5f else 1f
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

                    if (!isActiveTracking) {
                        FilterButton(
                            text = "AKTIVNI",
                            isActive = selectedFilter == MapViewModel.RadarFilter.ACTIVE,
                            onClick = { viewModel.setFilter(MapViewModel.RadarFilter.ACTIVE) }
                        )
                        FilterButton(
                            text = "DANAS",
                            isActive = selectedFilter == MapViewModel.RadarFilter.TODAY,
                            onClick = { viewModel.setFilter(MapViewModel.RadarFilter.TODAY) }
                        )
                        FilterButton(
                            text = "SVI",
                            isActive = selectedFilter == MapViewModel.RadarFilter.ALL,
                            onClick = { viewModel.setFilter(MapViewModel.RadarFilter.ALL) }
                        )
                    }
                }
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
                    onDismiss = { requestEnableGps() }
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
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterStart else Alignment.BottomCenter)
                .onSizeChanged { size ->
                    bottomNavSizePx = if (isLandscape) size.width else size.height
                }
        )
    }
}