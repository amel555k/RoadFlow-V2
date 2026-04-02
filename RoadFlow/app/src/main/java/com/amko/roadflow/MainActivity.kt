package com.amko.roadflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amko.roadflow.presentation.components.Sidebar
import com.amko.roadflow.presentation.screens.MainScreen
import com.amko.roadflow.presentation.screens.MapScreen
import com.amko.roadflow.ui.theme.RoadFlowTheme
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        setContent {
            RoadFlowTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                Sidebar(
                    onNavigate = { route ->
                        scope.launch {
                            drawerState.close()
                        }
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    drawerState = drawerState
                ) {
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(onOpenDrawer = {
                                scope.launch { drawerState.open() }
                            })
                        }
                        composable("map") {
                            MapScreen(onOpenDrawer = {
                                scope.launch { drawerState.open() }
                            })
                        }
                    }
                }
            }
        }
    }
}