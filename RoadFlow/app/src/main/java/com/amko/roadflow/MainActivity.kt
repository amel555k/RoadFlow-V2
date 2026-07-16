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
import com.amko.roadflow.presentation.screens.SoundSettingsScreen
import com.amko.roadflow.presentation.viewmodel.MainViewModel
import com.amko.roadflow.presentation.viewmodel.SoundViewModel
import com.amko.roadflow.ui.theme.RoadFlowTheme
import org.maplibre.android.MapLibre
import com.amko.roadflow.presentation.screens.HistoryScreen
import com.amko.roadflow.presentation.viewmodel.HistoryViewModel
import androidx.compose.runtime.collectAsState
import com.amko.roadflow.presentation.viewmodel.ThemeViewModel
import com.amko.roadflow.presentation.screens.SplashScreen
import com.amko.roadflow.domain.model.Canton
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    private var navControllerRef: NavHostController? = null
    private var pendingOpenMap = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        pendingOpenMap.value = intent?.getBooleanExtra(RadarTrackingService.EXTRA_OPEN_MAP, false) == true

        val prefs = getSharedPreferences("roadflow_prefs", MODE_PRIVATE)
        val hasFavoriteChoice = prefs.getString("favorite_canton", null) != null
        val startDestination = if (hasFavoriteChoice) "main" else "splash"

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
                val soundViewModel: SoundViewModel = viewModel()

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

                NavHost(navController = navController, startDestination = startDestination) {
                    composable("splash") {
                        val cityToCanton = remember {
                            com.amko.roadflow.data.local.RadarConfig.locations
                                .map { it.name to it.canton }
                        }
                        val cantonList = remember {
                            listOf(
                                Canton.UnskoSanski to "Unsko-sanski kanton",
                                Canton.Posavski to "Posavski kanton",
                                Canton.Tuzlanski to "Tuzlanski kanton",
                                Canton.ZenickoDobojski to "Zeničko-dobojski kanton",
                                Canton.BosanskoPodrinjski to "Bosansko-podrinjski kanton",
                                Canton.Srednjobosanski to "Srednjobosanski kanton",
                                Canton.HercegovackoNeretvanski to "Hercegovačko-neretvanski kanton",
                                Canton.Zapadnohercegovacki to "Zapadnohercegovački kanton",
                                Canton.Sarajevo to "Kanton Sarajevo",
                                Canton.Kanton10 to "Kanton 10",
                                Canton.BrckoDistrikt to "Brčko distrikt"
                            )
                        }
                        SplashScreen(
                            cantonList = cantonList,
                            cityToCanton = cityToCanton,
                            onSave = { canton, city ->
                                mainViewModel.saveFavoriteChoice(canton, city)
                                navController.navigate("main") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("main") {
                        MainScreen(
                            viewModel = mainViewModel,
                            themeViewModel = themeViewModel,
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
                            onNavigateToWidget = { navController.navigate("widget_settings") },
                            onNavigateToSound = { navController.navigate("sound_settings") }
                        )
                    }
                    composable("theme_settings") {
                        ThemeSettingsScreen(
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
                    composable("sound_settings") {
                        SoundSettingsScreen(
                            soundViewModel = soundViewModel,
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