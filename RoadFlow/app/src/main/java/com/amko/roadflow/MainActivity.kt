package com.amko.roadflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.amko.roadflow.data.local.RadarTrackingService
import com.amko.roadflow.presentation.screens.MainScreen
import com.amko.roadflow.presentation.screens.MapScreen
import com.amko.roadflow.presentation.screens.SettingsScreen
import com.amko.roadflow.presentation.screens.ThemeSettingsScreen
import com.amko.roadflow.presentation.screens.WidgetSettingsScreen
import com.amko.roadflow.presentation.viewmodel.MainViewModel
import com.amko.roadflow.ui.theme.RoadFlowTheme
import org.maplibre.android.MapLibre
import com.amko.roadflow.presentation.screens.HistoryScreen
import com.amko.roadflow.presentation.viewmodel.HistoryViewModel
import androidx.compose.runtime.collectAsState
import com.amko.roadflow.presentation.viewmodel.ThemeViewModel

class MainActivity : ComponentActivity() {

    private var navControllerRef: NavHostController? = null
    private var pendingOpenMap = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        pendingOpenMap.value = intent?.getBooleanExtra(RadarTrackingService.EXTRA_OPEN_MAP, false) == true

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()

            RoadFlowTheme(appTheme = themeMode) {
                val navController = rememberNavController()
                navControllerRef = navController

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val mainViewModel: MainViewModel = viewModel()
                val historyViewModel: HistoryViewModel = viewModel()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val shouldOpenMap by pendingOpenMap

                LaunchedEffect(shouldOpenMap) {
                    if (shouldOpenMap) {
                        navController.navigate("map") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        pendingOpenMap.value = false
                    }
                }

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            viewModel = mainViewModel,
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            viewModel = historyViewModel,
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable("map") {
                        MapScreen(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateToTheme = { navController.navigate("theme_settings") },
                            onNavigateToWidget = { navController.navigate("widget_settings") }
                        )
                    }
                    composable("theme_settings") {
                        ThemeSettingsScreen(
                            themeViewModel = themeViewModel,
                            mainViewModel=mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("widget_settings") {
                        WidgetSettingsScreen(
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(RadarTrackingService.EXTRA_OPEN_MAP, false)) {
            pendingOpenMap.value = true
        }
    }
}