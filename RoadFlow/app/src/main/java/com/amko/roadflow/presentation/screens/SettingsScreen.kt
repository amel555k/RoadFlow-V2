package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.R

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToWidget: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFD9D9D9))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF212143))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.back),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            ListItem(
                headlineContent = { Text("Theme", fontWeight = FontWeight.Medium) },
                modifier = Modifier.clickable { onNavigateToTheme() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray.copy(alpha = 0.5f))
            ListItem(
                headlineContent = { Text("Widget", fontWeight = FontWeight.Medium) },
                modifier = Modifier.clickable { onNavigateToWidget() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}