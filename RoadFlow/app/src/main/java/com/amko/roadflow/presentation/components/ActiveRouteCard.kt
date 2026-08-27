package com.amko.roadflow.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.data.local.RouteResult
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

@Composable
fun ActiveRouteCard(
    currentRouteResult: RouteResult?,
    isCalculatingRoute: Boolean,
    isActiveTracking: Boolean,
    userLat: Double?,
    userLng: Double?,
    currentSpeedKmh: Float?,
    traveledSegmentIndex: Int,
    isLandscape: Boolean,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = 16.dp,
    endPadding: Dp = 16.dp
) {
    var smoothedSpeedKmh by remember { mutableStateOf<Float?>(null) }
    var remainingDistanceMetersState by remember { mutableStateOf<Double?>(null) }
    var remainingDurationSeconds by remember { mutableStateOf<Double?>(null) }
    var frozenDurationSeconds by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(currentSpeedKmh) {
        val speed = currentSpeedKmh
        if (speed != null && speed >= 0f) {
            val smoothingFactor = 0.08f
            smoothedSpeedKmh = if (smoothedSpeedKmh == null) {
                speed
            } else {
                smoothedSpeedKmh!! + (speed - smoothedSpeedKmh!!) * smoothingFactor
            }
        }
    }

    LaunchedEffect(userLat, userLng, traveledSegmentIndex, currentRouteResult, isActiveTracking) {
        if (!isActiveTracking) {
            remainingDistanceMetersState = null
            remainingDurationSeconds = null
            frozenDurationSeconds = null
            return@LaunchedEffect
        }

        val route = currentRouteResult ?: return@LaunchedEffect
        val lat = userLat ?: return@LaunchedEffect
        val lng = userLng ?: return@LaunchedEffect
        if (route.coordinates.size < 2) return@LaunchedEffect

        val remainingDistance = remainingDistanceMeters(
            route.coordinates,
            traveledSegmentIndex,
            lat,
            lng
        )
        remainingDistanceMetersState = remainingDistance

        val stoppedThresholdKmh = 3f
        val speed = smoothedSpeedKmh
        val isMoving = speed != null && speed >= stoppedThresholdKmh

        if (isMoving) {
            val speedMetersPerSecond = speed!! / 3.6
            val calculatedDuration = if (speedMetersPerSecond > 0.1) {
                remainingDistance / speedMetersPerSecond
            } else {
                val routeAvgSpeed = if (route.durationSeconds > 0) {
                    route.distanceMeters / route.durationSeconds
                } else 0.0
                if (routeAvgSpeed > 0.1) remainingDistance / routeAvgSpeed else route.durationSeconds
            }
            remainingDurationSeconds = calculatedDuration
            frozenDurationSeconds = calculatedDuration
        } else {
            if (frozenDurationSeconds == null) {
                val routeAvgSpeed = if (route.durationSeconds > 0) {
                    route.distanceMeters / route.durationSeconds
                } else 0.0
                frozenDurationSeconds = if (routeAvgSpeed > 0.1) {
                    remainingDistance / routeAvgSpeed
                } else {
                    route.durationSeconds
                }
            }
            remainingDurationSeconds = frozenDurationSeconds
        }
    }

    val effectiveDistanceMeters = currentRouteResult?.let { route ->
        if (isActiveTracking) remainingDistanceMetersState ?: route.distanceMeters
        else route.distanceMeters
    }

    val topPadding = if (isLandscape) 8.dp else 20.dp
    val sideStartPadding = if (isExpanded) startPadding else 12.dp
    val sideEndPadding = if (isExpanded) endPadding else 12.dp

    Box(
        modifier = modifier
            .padding(top = topPadding, start = sideStartPadding, end = sideEndPadding)
            .let {
                if (isExpanded) {
                    if (isLandscape) it.widthIn(max = 400.dp) else it.fillMaxWidth()
                } else {
                    it.wrapContentWidth()
                }
            }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4D7079)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .let {
                    if (isExpanded) {
                        if (isLandscape) it.widthIn(max = 400.dp) else it.fillMaxWidth()
                    } else {
                        it.wrapContentWidth()
                    }
                }
                .animateContentSize(animationSpec = tween(durationMillis = 250))
        ) {
            if (!isExpanded) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCalculatingRoute) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = effectiveDistanceMeters?.let { formatDistance(it) } ?: "--",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onExpandedChange(true) },
                        modifier = Modifier.width(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Prosiri",
                            tint = Color.White,
                            modifier = Modifier.width(16.dp)
                        )
                    }
                }
            } else {
                val verticalPadding = if (isLandscape) 4.dp else 10.dp
                val horizontalPadding = if (isLandscape) 8.dp else 12.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onExpandedChange(false) },
                        modifier = Modifier.width(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Suzi",
                            tint = Color.White,
                            modifier = Modifier.width(16.dp)
                        )
                    }

                    if (isCalculatingRoute) {
                        Row(
                            modifier = Modifier.width(0.dp).weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Izračunavam...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isLandscape) 14.sp else 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    } else if (currentRouteResult != null) {
                        val route = currentRouteResult
                        val effectiveDurationSeconds = if (isActiveTracking) {
                            remainingDurationSeconds ?: route.durationSeconds
                        } else {
                            route.durationSeconds
                        }

                        val totalMinutes = (effectiveDurationSeconds / 60).toInt()
                        val hours = totalMinutes / 60
                        val remainingMinutes = totalMinutes % 60

                        val timeFormatted = when {
                            hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
                            hours > 0 -> "${hours}h"
                            remainingMinutes > 0 -> "${remainingMinutes} min"
                            else -> "1 min"
                        }

                        val km = effectiveDistanceMeters?.let { formatDistance(it) } ?: "--"
                        val eta = LocalTime.now().plusSeconds(effectiveDurationSeconds.toLong())
                        val etaFormatted = eta.format(DateTimeFormatter.ofPattern("HH:mm"))

                        val infoFontSize = if (isLandscape) 13.sp else 14.sp
                        val itemSpacer = if (isLandscape) 4.dp else 8.dp

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = km,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = infoFontSize,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.width(itemSpacer))
                            Text(
                                text = timeFormatted,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = infoFontSize,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.width(itemSpacer))
                            Text(
                                text = etaFormatted,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = infoFontSize,
                                textAlign = TextAlign.Center
                            )
                        }

                        IconButton(
                            onClick = onClearRoute,
                            modifier = Modifier.width(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Ukloni rutu",
                                tint = Color.White,
                                modifier = Modifier.width(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

}