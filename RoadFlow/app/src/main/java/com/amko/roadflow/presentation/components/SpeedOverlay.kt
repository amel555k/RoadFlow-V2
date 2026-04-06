package com.amko.roadflow.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpeedOverlay(
    isInRadarZone: Boolean,
    speedLimitInZone: Int,
    currentSpeed: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isInRadarZone && speedLimitInZone > 0) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(color = Color.White, shape = CircleShape)
                    .border(width = 8.dp, color = Color.Red, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$speedLimitInZone",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }

        Box(
            modifier = Modifier
                .background(
                    color = Color(0xCC1E2736),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val speedDisplayColor = if (isInRadarZone) {
                val diff = currentSpeed - speedLimitInZone
                when {
                    diff <= 0 -> Color.White
                    diff <= 5 -> Color(0xFFFFEB3B)
                    else -> Color(0xFFFF5252)
                }
            } else {
                Color.White
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${currentSpeed.toInt()}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = speedDisplayColor
                )
                Text(
                    text = "km/h",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFAAAAAA)
                )
            }
        }
    }
}