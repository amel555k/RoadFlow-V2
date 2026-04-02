package com.amko.roadflow.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            ModalDrawerSheet {

                NavigationDrawerItem(
                    label = { Text("Glavni Ekran") },
                    selected = false,
                    onClick = { onNavigate("main") }
                )
                NavigationDrawerItem(
                    label = { Text("Mape") },
                    selected = false,
                    onClick = { onNavigate("map") }
                )
            }
        }
    ) {
        content()
    }
}