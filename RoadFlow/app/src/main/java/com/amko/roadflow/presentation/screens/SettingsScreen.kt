package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.presentation.components.BottomNavBar

@Composable
fun SettingsScreen(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToWidget: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {



            Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                ListItem(
                    headlineContent = { Text("Theme", fontWeight = FontWeight.Medium) },
                    modifier = Modifier.clickable { onNavigateToTheme() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
                ListItem(
                    headlineContent = { Text("Widget", fontWeight = FontWeight.Medium) },
                    modifier = Modifier.clickable { onNavigateToWidget() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        BottomNavBar(
            currentRoute = currentRoute,
            onNavigate = onNavigate
        )
    }
}