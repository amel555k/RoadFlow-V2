package com.amko.roadflow.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.domain.model.RadarData

@Composable
fun RadarInfoCard(
    radar: RadarData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false
) {
    Card(
        modifier = if (isVertical) modifier.width(220.dp) else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF212143)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(if (isVertical) 16.dp else 24.dp)) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (radar.coordinate?.stacionaran == true) {
                        Text(
                            text = radar.location,
                            color = Color.White,
                            fontSize = if (isVertical) 18.sp else 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = radar.location,
                            color = Color.White,
                            fontSize = if (isVertical) 18.sp else 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = radar.city,
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("✕", color = Color.White, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = radar.time,
                color = Color.White,
                fontSize = 18.sp
            )
            if (radar.speedLimit != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "OGRANIČENJE: ${radar.speedLimit} km/h",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}