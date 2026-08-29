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
import android.location.Location

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
private const val SNAP_DISTANCE_METERS = 12.0
private const val SNAP_MAX_ANGLE_DIFF_DEGREES = 55.0
private const val SNAP_DISTANCE_HYSTERESIS_METERS = 12.0
private const val SNAP_ANGLE_HYSTERESIS_DEGREES = 70.0
private const val OFF_ROUTE_REROUTE_DELAY_NANOS = 5_000_000_000
private const val OFF_ROUTE_DISTANCE_METERS = 5.0
private const val REROUTE_COOLDOWN_NANOS = 10_000_000_000L
private const val WRONG_DIRECTION_ANGLE_DEGREES = 100.0
private const val ARRIVAL_DISTANCE_METERS = 30.0
private const val SHORT_ROUTE_THRESHOLD_METERS = 5000.0


private fun formatDistance(distanceMeters: Double): String {
    return if (distanceMeters < 1000.0) {
        val roundedTo50 = (Math.round(distanceMeters / 50.0) * 50).toInt()
        val clamped = if (roundedTo50 <= 0) 50 else roundedTo50
        "$clamped m"
    } else {
        val km = Math.round(distanceMeters / 1000.0)
        "$km km"
    }
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

private fun remainingDistanceMeters(
    coordinates: List<Pair<Double, Double>>,
    fromSegmentIndex: Int,
    currentLat: Double,
    currentLng: Double
): Double {
    if (coordinates.size < 2) return 0.0

    val mPerLat = 111320.0
    fun mPerLng(atLat: Double) = 111320.0 * Math.cos(Math.toRadians(atLat))

    val clampedIndex = fromSegmentIndex.coerceIn(0, coordinates.size - 2)

    var total = 0.0

    val (segALat, segALng) = coordinates[clampedIndex]
    val (segBLat, segBLng) = coordinates[clampedIndex + 1]
    val dxUser = (segBLng - currentLng) * mPerLng(currentLat)
    val dyUser = (segBLat - currentLat) * mPerLat
    total += Math.sqrt(dxUser * dxUser + dyUser * dyUser)

    for (i in (clampedIndex + 1) until coordinates.size - 1) {
        val (aLat, aLng) = coordinates[i]
        val (bLat, bLng) = coordinates[i + 1]
        val dx = (bLng - aLng) * mPerLng(aLat)
        val dy = (bLat - aLat) * mPerLat
        total += Math.sqrt(dx * dx + dy * dy)
    }

    return total
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

private data class SnapResult(val lat: Double, val lng: Double, val bearing: Float, val distanceMeters: Double, val isAccepted: Boolean, val segmentIndex: Int)

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
    var bestSegmentIndex = 0
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
            bestSegmentIndex = i
            found = true
        }
    }

    if (!found) return null

    val distanceThreshold = if (wasSnappedLastTime) SNAP_DISTANCE_HYSTERESIS_METERS else SNAP_DISTANCE_METERS
    if (bestDistance > distanceThreshold) {
        return SnapResult(bestLat, bestLng, bestSegmentBearing.toFloat(), bestDistance, isAccepted = false, segmentIndex = bestSegmentIndex)
    }

    val angleThreshold = if (wasSnappedLastTime) SNAP_ANGLE_HYSTERESIS_DEGREES else SNAP_MAX_ANGLE_DIFF_DEGREES
    val diff = angleDiff(userBearing, bestSegmentBearing)
    if (diff > angleThreshold) {
        return SnapResult(bestLat, bestLng, bestSegmentBearing.toFloat(), bestDistance, isAccepted = false, segmentIndex = bestSegmentIndex)
    }

    return SnapResult(bestLat, bestLng, bestSegmentBearing.toFloat(), bestDistance, isAccepted = true, segmentIndex = bestSegmentIndex)
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
    var showPermissionExplanation by remember { mutableStateOf(false) }
    var locationFound by remember { mutableStateOf(hadSavedCameraOnEnter) }
    var gpsWasDisabled by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var isGpsEnabled by remember { mutableStateOf(true) }
    var showGpsLoading by remember { mutableStateOf(false) }
    var lastSnapWasSuccessful by remember { mutableStateOf(false) }
    var lastSnapDistanceMeters by remember { mutableStateOf<Double?>(null) }
    var lastSnapWrongDirection by remember { mutableStateOf(false) }
    var suppressSpeedUntilNanos by remember { mutableStateOf(0L) }
    var displaySpeedKmh by remember { mutableStateOf<Float?>(null) }
    var traveledSegmentIndex by remember { mutableStateOf(0) }
    var offRouteSinceNanos by remember { mutableStateOf<Long?>(null) }
    var lastRerouteNanos by remember { mutableStateOf(0L) }
    var isRerouting by remember { mutableStateOf(false) }

    var routeAlternatives by remember { mutableStateOf<List<RouteResult>>(emptyList()) }
    var selectedRouteIndex by remember { mutableStateOf(0) }
    val currentRouteResult: RouteResult? = routeAlternatives.getOrNull(selectedRouteIndex)
    var labelFractions by remember { mutableStateOf<Map<Int, Double>>(emptyMap()) }
    var isRouteCardExpanded by remember { mutableStateOf(true) }

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

        val shortestRoute = results.minByOrNull { it.distanceMeters }
        val effectiveResults = if (shortestRoute != null && shortestRoute.distanceMeters < SHORT_ROUTE_THRESHOLD_METERS) {
            listOf(shortestRoute)
        } else {
            results
        }

        routeAlternatives = effectiveResults
        selectedRouteIndex = 0
        traveledSegmentIndex = 0
        isCalculatingRoute = false

        if (effectiveResults.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            boundsBuilder.include(LatLng(uLoc.latitude, uLoc.longitude))
            effectiveResults.forEach { r ->
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

            val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!gpsEnabled && isGpsEnabled && fineGranted) {
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
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                as android.location.LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

        if (!gpsEnabled) {
            coroutineScope.launch {
                delay(200)
                requestEnableGps()
            }
        } else {
            showGpsLoading = true
            coroutineScope.launch {
                val lastKnown = viewModel.locationService.getLastKnownLocation()
                if (lastKnown != null) {
                    viewModel.locationService.setInitialLocation(lastKnown)
                }
                viewModel.locationService.startPassiveTracking()
            }
        }
    }


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            var willAskBackground = false

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val backgroundGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!backgroundGranted) {
                    willAskBackground = true
                    backgroundPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }

            if (!willAskBackground) {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                        as android.location.LocationManager
                val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

                if (!gpsEnabled) {
                    coroutineScope.launch {
                        delay(200)
                        requestEnableGps()
                    }
                } else {
                    showGpsLoading = true
                    coroutineScope.launch {
                        val lastKnown = viewModel.locationService.getLastKnownLocation()
                        if (lastKnown != null) {
                            viewModel.locationService.setInitialLocation(lastKnown)
                        }
                        viewModel.locationService.startPassiveTracking()
                    }
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
        }
    }
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
                Lifecycle.Event.ON_RESUME -> {
                    mapViewRef.onResume()
                    if (selectedDestination != null && routeAlternatives.isNotEmpty()) {
                        coroutineScope.launch {
                            val currentLoc = userLocation ?: return@launch
                            val activeRouteCoords = routeAlternatives.getOrNull(selectedRouteIndex)?.coordinates
                            val distanceFromRoute = if (activeRouteCoords != null && activeRouteCoords.isNotEmpty()) {
                                val snap = snapToRoute(currentLoc.latitude, currentLoc.longitude, userHeading, activeRouteCoords, false)
                                snap?.distanceMeters
                            } else null

                            val isFarFromRoute = distanceFromRoute == null || distanceFromRoute > OFF_ROUTE_DISTANCE_METERS
                            if (isFarFromRoute && !isRerouting) {
                                val destination = selectedDestination ?: return@launch
                                isRerouting = true
                                try {
                                    val results = routingService.getRoutes(
                                        currentLoc.latitude,
                                        currentLoc.longitude,
                                        destination.latitude,
                                        destination.longitude,
                                        userHeading
                                    )
                                    if (results.isNotEmpty()) {
                                        routeAlternatives = results
                                        selectedRouteIndex = 0
                                        labelFractions = emptyMap()
                                        traveledSegmentIndex = 0
                                        lastRerouteNanos = System.nanoTime()
                                    }
                                } catch (e: Exception) {
                                } finally {
                                    isRerouting = false
                                    offRouteSinceNanos = null
                                }
                            }
                        }
                    }
                }
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

    LaunchedEffect(currentSpeed, suppressSpeedUntilNanos) {
        val now = System.nanoTime()
        displaySpeedKmh = when {
            currentSpeed == null -> null
            now < suppressSpeedUntilNanos -> 0f
            else -> currentSpeed
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
                remainingMinutes > 0 -> "${remainingMinutes} min"
                else -> "1 min"
            }
            val km = formatDistance(route.distanceMeters)
            return Pair(timeFormatted, km)
        }
        routeAlternatives.forEachIndexed { index, route ->
            if (route.coordinates.isEmpty()) return@forEachIndexed
            val visibleCoordinates = if (isActiveTracking && index == selectedRouteIndex) {
                route.coordinates.drop(traveledSegmentIndex)
            } else {
                route.coordinates
            }
            if (visibleCoordinates.isEmpty()) return@forEachIndexed
            val points = visibleCoordinates.map { Point.fromLngLat(it.second, it.first) }
            val lineString = LineString.fromLngLats(points)
            val feature = Feature.fromGeometry(lineString)
            feature.addBooleanProperty("isSelected", index == selectedRouteIndex)
            feature.addNumberProperty("routeIndex", index)

            val fraction = labelFractions[index] ?: 0.5
            val mid = pointAtFraction(visibleCoordinates, fraction)

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
        lastSnapWrongDirection = if (snapResult != null) {
            angleDiff(userHeading, snapResult.bearing.toDouble()) > WRONG_DIRECTION_ANGLE_DEGREES
        } else {
            false
        }
        if (snapResult?.isAccepted == true) {
            traveledSegmentIndex = maxOf(traveledSegmentIndex, snapResult.segmentIndex)
        }
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
            animator.updateFix(effectiveLat, effectiveLng, targetRotation, currentSpeed ?: 0f, forceSnap = true)
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
            animator.updateFix(effectiveLat, effectiveLng, targetRotation, currentSpeed ?: 0f, forceSnap = true)
            pushUserGeoJson(effectiveLat, effectiveLng, targetRotation, force = true)
        } else {
            val startLatLng = LatLng(animator.renderedPos.value.lat, animator.renderedPos.value.lng)
            val targetLatLng = LatLng(loc.latitude, loc.longitude)
            val jumpDistanceMeters = startLatLng.distanceTo(targetLatLng)

            if (jumpDistanceMeters > 300.0 || isTransitioningToTracking) {
                suppressSpeedUntilNanos = System.nanoTime() + 3_000_000_000L
                animator.updateFix(effectiveLat, effectiveLng, targetRotation, 0f, forceSnap = true)
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
                animator.updateFix(effectiveLat, effectiveLng, targetRotation, currentSpeed ?: 0f, forceSnap = false)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)

            if (!isActiveTracking) {
                offRouteSinceNanos = null
                continue
            }

            val distance = lastSnapDistanceMeters
            val isOffRoute = !lastSnapWasSuccessful && distance != null && distance > OFF_ROUTE_DISTANCE_METERS
            val isWrongDirection = isOffRoute && lastSnapWrongDirection

            if (!isOffRoute) {
                offRouteSinceNanos = null
                continue
            }

            if (!isWrongDirection) {
                if (offRouteSinceNanos == null) {
                    offRouteSinceNanos = System.nanoTime()
                    continue
                }

                val startedAt = offRouteSinceNanos ?: continue
                val elapsed = System.nanoTime() - startedAt
                if (elapsed < OFF_ROUTE_REROUTE_DELAY_NANOS) continue
            }

            val now = System.nanoTime()
            if (now - lastRerouteNanos < REROUTE_COOLDOWN_NANOS) continue
            if (isRerouting) continue

            val destination = selectedDestination ?: continue
            val currentLoc = userLocation ?: continue

            isRerouting = true
            lastRerouteNanos = now
            try {
                val results = routingService.getRoutes(
                    currentLoc.latitude,
                    currentLoc.longitude,
                    destination.latitude,
                    destination.longitude,
                    userHeading
                )
                if (results.isNotEmpty()) {
                    routeAlternatives = results
                    selectedRouteIndex = 0
                    labelFractions = emptyMap()
                    traveledSegmentIndex = 0
                }
            } catch (e: Exception) {
            } finally {
                isRerouting = false
                offRouteSinceNanos = null
            }
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)

            if (!isActiveTracking) continue

            val destination = selectedDestination ?: continue
            val currentLoc = userLocation ?: continue

            val destLocation = Location("dest").apply {
                latitude = destination.latitude
                longitude = destination.longitude
            }
            val userLoc = Location("user").apply {
                latitude = currentLoc.latitude
                longitude = currentLoc.longitude
            }
            val distanceToDestination = userLoc.distanceTo(destLocation)

            if (distanceToDestination <= ARRIVAL_DISTANCE_METERS) {
                clearRoute()
            }
        }
    }

    var bottomNavSizePx by remember { mutableStateOf(0) }
    val bottomNavSizeDp = with(androidx.compose.ui.platform.LocalDensity.current) { bottomNavSizePx.toDp() }

    suspend fun toggleTracking() {
        val map = mapRef ?: return
        if (isActiveTracking) {
            isCameraLocked = true
            viewModel.locationService.stopActiveTracking()
            viewModel.stopBackgroundTracking()
            alertService.stopAlerts()

            val activeRoute = routeAlternatives.getOrNull(selectedRouteIndex)
            val dest = selectedDestination
            if (activeRoute != null && dest != null) {
                routeAlternatives = listOf(activeRoute)
                selectedRouteIndex = 0
            }

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

    suspend fun locateMe() {
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            showPermissionExplanation = true
            return
        }

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

                        map.setLatLngBoundsForCameraTarget(null)

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

                            setupMapStyle(
                                context = context,
                                style = style,
                                radarIconId = RADAR_ICON_ID,
                                radarIconStacionarniId = RADAR_ICON_STACIONARNI_ID,
                                userIconId = USER_ICON_ID,
                                destinationIconId = DESTINATION_ICON_ID,
                                radarSourceId = RADAR_SOURCE_ID,
                                radarLayerId = RADAR_LAYER_ID,
                                userSourceId = USER_SOURCE_ID,
                                userLayerId = USER_LAYER_ID,
                                destinationSourceId = DESTINATION_SOURCE_ID,
                                destinationLayerId = DESTINATION_LAYER_ID,
                                routeAltSourceId = ROUTE_ALT_SOURCE_ID,
                                routeAltLayerId = ROUTE_ALT_LAYER_ID,
                                routeAltHitareaLayerId = ROUTE_ALT_HITAREA_LAYER_ID,
                                routeLabelSourceId = ROUTE_LABEL_SOURCE_ID,
                                routeLabelLayerId = ROUTE_LABEL_LAYER_ID,
                                routeAltLabelSourceId = ROUTE_ALT_LABEL_SOURCE_ID,
                                routeAltLabelLayerId = ROUTE_ALT_LABEL_LAYER_ID
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
                if (!isActiveTracking && !isCalculatingRoute && isGpsEnabled && locationFound) {
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
                ActiveRouteCard(
                    currentRouteResult = currentRouteResult,
                    isCalculatingRoute = isCalculatingRoute,
                    isActiveTracking = isActiveTracking,
                    userLat = userLocation?.latitude,
                    userLng = userLocation?.longitude,
                    currentSpeedKmh = currentSpeed,
                    traveledSegmentIndex = traveledSegmentIndex,
                    isLandscape = isLandscape,
                    isExpanded = isRouteCardExpanded,
                    onExpandedChange = { isRouteCardExpanded = it },
                    onClearRoute = { clearRoute() },
                    modifier = Modifier.align(
                        if (isLandscape && isRouteCardExpanded) Alignment.TopStart
                        else if (isRouteCardExpanded) Alignment.TopCenter
                        else Alignment.TopStart
                    ),
                    startPadding = if (isLandscape) 16.dp else 16.dp,
                    endPadding = if (isLandscape) 180.dp else 16.dp
                )
            }


            if (isActiveTracking) {
                val hasActiveRoute = currentRouteResult != null
                val hasExpandedRoute = hasActiveRoute && isRouteCardExpanded
                SpeedOverlay(
                    isInRadarZone = isInRadarZone,
                    speedLimitInZone = speedLimitInZone,
                    currentSpeed = displaySpeedKmh,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = when {
                                isLandscape && hasExpandedRoute -> 60.dp
                                isLandscape -> 20.dp
                                hasExpandedRoute -> 130.dp
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
                    }

                    if (didInitialZoom && locationFound && isGpsEnabled) {
                        TrackingToggleButton(
                            isActiveTracking = isActiveTracking,
                            isPickingOnMap = isPickingOnMap,
                            onToggle = { toggleTracking() }
                        )
                    } else if (!hasLocationPermission || !isGpsEnabled || (gpsWasDisabled && !locationFound)) {
                        LocateMeButton(
                            onClick = { coroutineScope.launch { locateMe() } }
                        )
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
                        TrackingToggleButton(
                            isActiveTracking = isActiveTracking,
                            isPickingOnMap = isPickingOnMap,
                            onToggle = { toggleTracking() }
                        )
                    } else if (!hasLocationPermission || !isGpsEnabled || (gpsWasDisabled && !locationFound)) {
                        LocateMeButton(
                            onClick = { coroutineScope.launch { locateMe() } }
                        )
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
                    message = "Molimo uključite lokaciju kako bi aktivno praćenje radilo.",
                    onDismiss = { requestEnableGps() },
                    onCancel = { showNoGps = false }
                )
            }

            if (showPermissionExplanation) {
                LocationPermissionExplanationDialog(
                    onDismiss = { showPermissionExplanation = false },
                    onConfirm = {
                        showPermissionExplanation = false
                        coroutineScope.launch {
                            delay(200)
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
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
            showLabels = false,
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterStart else Alignment.BottomCenter)
                .onSizeChanged { size ->
                    bottomNavSizePx = if (isLandscape) size.width else size.height
                }
        )
    }
}