package com.amko.roadflow.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

@Composable
fun TrackingToggleButton(
    isActiveTracking: Boolean,
    isPickingOnMap: Boolean,
    onToggle: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    var trackingButtonScale by remember { mutableStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = trackingButtonScale,
        animationSpec = tween(durationMillis = 150),
        label = "button_scale"
    )
    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = {
            if (isPickingOnMap) return@Button
            coroutineScope.launch {
                trackingButtonScale = 0.85f
                delay(150)
                trackingButtonScale = 1f
                onToggle()
            }
        },
        enabled = !isPickingOnMap,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActiveTracking)
                Color(0xFFF44336)
            else
                Color(0xFFD2F7FF),
            disabledContainerColor = Color(0xFFD2F7FF).copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 15.dp),
        modifier = modifier
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
                    Color.White
                else
                    Color(0xFF004E5A),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isActiveTracking) "ZAUSTAVI" else "KRENI",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActiveTracking)
                    Color.White
                else
                    Color(0xFF004E5A)
            )
        }
    }
}