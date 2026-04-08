package com.amko.roadflow.presentation.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun Sidebar(
    onNavigate: (String) -> Unit,
    drawerState: DrawerState,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFF2E2E5E)) {
                NavigationDrawerItem(
                    label = { Text("Lista", color = Color.White) },
                    selected = false,
                    onClick = { onNavigate("main") }
                )
                NavigationDrawerItem(
                    label = { Text("Mape", color = Color.White) },
                    selected = false,
                    onClick = { onNavigate("map") }
                )
                NavigationDrawerItem(
                    label = { Text("Postavke", color = Color.White) },
                    selected = false,
                    onClick = { onNavigate("settings") }
                )
            }
        }
    ) {
        content()
    }
}