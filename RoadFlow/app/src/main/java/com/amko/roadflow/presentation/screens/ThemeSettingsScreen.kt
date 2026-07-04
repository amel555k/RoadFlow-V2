package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.R

@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf("System default") }
    val themes = listOf("Light mode", "Dark mode", "System default")

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
                text = "Theme",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            themes.forEach { theme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTheme = theme }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = theme, fontSize = 16.sp)
                    RadioButton(
                        selected = (theme == selectedTheme),
                        onClick = { selectedTheme = theme },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF212143))
                    )
                }
                if (theme != themes.last()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray.copy(alpha = 0.5f))
                }
            }
        }
    }
}