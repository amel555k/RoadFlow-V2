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
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF212143))
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute == item.route

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clickable { onNavigate(item.route) }
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isSelected) item.filledIcon else item.outlineIcon
                    ),
                    contentDescription = item.label,
                    tint = if (isSelected) Color.White else Color.LightGray,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = item.label,
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else Color.LightGray,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}