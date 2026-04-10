package com.amko.roadflow.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

@Composable
fun Sidebar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    drawerState: DrawerState,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF2E2E5E),
                drawerShape = RectangleShape
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                val drawerItemColors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = Color(0xFF4A4A8F),
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = Color.White,
                    unselectedTextColor = Color.LightGray,
                    selectedIconColor = Color.White,
                    unselectedIconColor = Color.LightGray
                )

                NavigationDrawerItem(
                    label = { Text("Lista") },
                    selected = currentRoute == "main",
                    onClick = { onNavigate("main") },
                    colors = drawerItemColors,
                    shape = RectangleShape
                )

                NavigationDrawerItem(
                    label = { Text("Mape") },
                    selected = currentRoute == "map",
                    onClick = { onNavigate("map") },
                    colors = drawerItemColors,
                    shape = RectangleShape
                )

                NavigationDrawerItem(
                    label = { Text("Postavke") },
                    selected = currentRoute == "settings",
                    onClick = { onNavigate("settings") },
                    colors = drawerItemColors,
                    shape = RectangleShape
                )
            }
        }
    ) {
        content()
    }
}