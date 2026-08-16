package com.amko.roadflow.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoutingRecognitionOverlay(
    isSnapped: Boolean,
    distanceToRouteMeters: Double?,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSnapped) Color(0xFF2E7D32) else Color(0xFFB71C1C)
    val statusText = if (isSnapped) "DA" else "NE"

    val displayText = if (isSnapped) {
        "Prepoznata ruting cesta: $statusText"
    } else {
        val distFormatted = distanceToRouteMeters?.let { String.format("%.1f", it) } ?: "-"
        "Prepoznata ruting cesta: $statusText\nUdaljenost od rute: ${distFormatted}m"
    }

    Text(
        text = displayText,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = modifier
            .background(color = backgroundColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}