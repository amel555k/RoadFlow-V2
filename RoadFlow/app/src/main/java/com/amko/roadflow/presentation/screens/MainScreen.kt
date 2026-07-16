package com.amko.roadflow.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.domain.model.RadarData
import com.amko.roadflow.presentation.viewmodel.MainViewModel
import com.amko.roadflow.presentation.viewmodel.RadarListItem
import java.time.format.DateTimeFormatter
import com.amko.roadflow.presentation.components.NoConnectionDialog
import androidx.compose.foundation.layout.statusBarsPadding
import com.amko.roadflow.presentation.components.BottomNavBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.amko.roadflow.presentation.components.RadarItem
import com.amko.roadflow.presentation.components.CantonPickerDropdown

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    android.util.Log.d("ROADFLOW1", "MainScreen composed viewModel=${System.identityHashCode(viewModel)}")

    val flatList by viewModel.uiList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedCanton by viewModel.selectedCanton.collectAsState()
    val showNoInternet by viewModel.showNoInternet.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()

    android.util.Log.d("ROADFLOW1", "MainScreen recompose: flatList.size=${flatList.size} isLoading=$isLoading")
    val cityList = remember {
        com.amko.roadflow.data.local.RadarConfig.locations
            .map { it.name }
            .distinct()
            .sorted()
    }

    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }


    var isDropdownOpen by remember { mutableStateOf(false) }

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

    val canPullToRefresh by viewModel.canPullToRefresh.collectAsState()

    val selectedCantonLabel = cantonList.firstOrNull { it.first == selectedCanton }?.second ?: ""
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()) {

        Column(modifier = Modifier.fillMaxSize()) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RoadFlow",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondary)
                        .clickable { isDropdownOpen = !isDropdownOpen }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedCantonLabel,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isDropdownOpen) "▲" else "▼",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        val listContent: @Composable () -> Unit = {
                            if (flatList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nema radara za odabrani kanton.",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontSize = 16.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    items(
                                        items = flatList,
                                        key = { item ->
                                            when (item) {
                                                is RadarListItem.CityHeader -> "header_${item.city}"
                                                is RadarListItem.RadarEntry -> "radar_${item.radar.city}_${item.radar.time}_${item.radar.location}"
                                                is RadarListItem.Spacer -> item.id
                                            }
                                        },
                                        contentType = { item ->
                                            when (item) {
                                                is RadarListItem.CityHeader -> 0
                                                is RadarListItem.RadarEntry -> 1
                                                is RadarListItem.Spacer -> 2
                                            }
                                        }
                                    ) { item ->
                                        when (item) {
                                            is RadarListItem.CityHeader -> {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 10.dp)
                                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        text = item.city,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            is RadarListItem.RadarEntry -> {
                                                RadarItem(radar = item.radar)
                                            }
                                            is RadarListItem.Spacer -> {
                                                Spacer(modifier = Modifier.height(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (canPullToRefresh) {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.refreshData() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                listContent()
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                listContent()
                            }
                        }
                    }
                }

                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate
                )
            }

            if (isDropdownOpen) {
                CantonPickerDropdown(
                    cantonList = cantonList,
                    selectedCanton = selectedCanton,
                    onCantonSelected = { canton ->
                        viewModel.selectCanton(canton)
                        isDropdownOpen = false
                    },
                    onDismiss = { isDropdownOpen = false }
                )
            }

        if (showNoInternet) {
            NoConnectionDialog(
                title = "Nema internet konekcije",
                message = "Molimo provjerite da li su uključeni WiFi ili mobilni podaci.",
                onDismiss = { viewModel.showNoInternet.value = false }
            )
        }
    }
}
