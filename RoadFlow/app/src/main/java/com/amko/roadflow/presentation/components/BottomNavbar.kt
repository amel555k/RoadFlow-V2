package com.amko.roadflow.presentation.components

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

data class BottomNavItem(
    val route: String,
    val label: String,
    val filledIcon: Int,
    val outlineIcon: Int
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = "main",
        label = "Lista",
        filledIcon = R.drawable.ic_home_filled,
        outlineIcon = R.drawable.ic_home_outline
    ),
    BottomNavItem(
        route = "map",
        label = "Mapa",
        filledIcon = R.drawable.ic_map_filled,
        outlineIcon = R.drawable.ic_map_outline
    ),
    BottomNavItem(
        route = "history",
        label = "Kalendar",
        filledIcon = R.drawable.ic_history_filled,
        outlineIcon = R.drawable.ic_history_outline
    ),
    BottomNavItem(
        route = "settings",
        label = "Postavke",
        filledIcon = R.drawable.ic_settings_filled,
        outlineIcon = R.drawable.ic_settings_outline
    )
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    isVertical: Boolean = false,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    val iconSize = if (showLabels) 24.dp else 30.dp
    val verticalPadding = if (showLabels) 12.dp else 16.dp
    val horizontalPadding = if (showLabels) 4.dp else 12.dp

    if (isVertical) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isSelected) item.filledIcon else item.outlineIcon
                        ),
                        contentDescription = item.label,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(iconSize)
                    )
                    if (showLabels) {
                        Text(
                            text = item.label,
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    } else {
        val rowIconSize = if (showLabels) 26.dp else 32.dp
        val rowVerticalPadding = if (showLabels) 4.dp else 12.dp
        val rowHorizontalPadding = if (showLabels) 20.dp else 24.dp

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .navigationBarsPadding()
                .padding(vertical = if (showLabels) 8.dp else 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = rowHorizontalPadding, vertical = rowVerticalPadding)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isSelected) item.filledIcon else item.outlineIcon
                        ),
                        contentDescription = item.label,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(rowIconSize)
                    )
                    if (showLabels) {
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}