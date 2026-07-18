package com.amko.roadflow.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.amko.roadflow.R
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.presentation.components.AppDropdown
import com.amko.roadflow.presentation.viewmodel.MainViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ThemeSettingsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val currentCanton by mainViewModel.selectedCanton.collectAsState()

    val prefs = mainViewModel.getApplication<android.app.Application>()
        .getSharedPreferences("roadflow_prefs", android.content.Context.MODE_PRIVATE)

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

    val initialCanton = currentCanton ?: cantonList.first().first
    val initialCity = prefs.getString("favorite_city", "") ?: ""

    var selectedCanton by remember { mutableStateOf(initialCanton) }
    var selectedCity by remember { mutableStateOf(initialCity) }

    val cityList = remember(selectedCanton) {
        RadarConfig.locations
            .filter { it.canton == selectedCanton }
            .map { it.name }
            .distinct()
            .sorted()
    }
    var cantonExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = cantonExpanded || cityExpanded) {
        cantonExpanded = false
        cityExpanded = false
    }

    val hasChanges = selectedCanton != initialCanton || selectedCity != initialCity

    val selectedCantonLabel = cantonList.firstOrNull { it.first == selectedCanton }?.second ?: ""

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.back),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Postavke prikaza",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Omiljeni kanton:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                AppDropdown(
                    options = cantonList,
                    selectedLabel = selectedCantonLabel,
                    selectedValue = selectedCanton,
                    expanded = cantonExpanded,
                    onExpandedChange = { cantonExpanded = it },
                    onOptionSelected = { canton ->
                        if (canton != selectedCanton) {
                            val filteredCities = RadarConfig.locations
                                .filter { it.canton == canton }
                                .map { it.name }
                                .distinct()
                                .sorted()
                            selectedCity = filteredCities.firstOrNull() ?: ""
                            selectedCanton = canton
                        }
                        cantonExpanded = false
                    },
                    fieldBackground = MaterialTheme.colorScheme.surface,
                    fieldText = MaterialTheme.colorScheme.onSurface,
                    fieldTextMuted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fieldArrow = MaterialTheme.colorScheme.onSurface,
                    cornerRadius = 8.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Omiljeni grad:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                AppDropdown(
                    options = cityList.map { it to it },
                    selectedLabel = selectedCity,
                    selectedValue = selectedCity,
                    expanded = cityExpanded,
                    onExpandedChange = { cityExpanded = it },
                    onOptionSelected = { city ->
                        selectedCity = city
                        cityExpanded = false
                    },
                    fieldBackground = MaterialTheme.colorScheme.surface,
                    fieldText = MaterialTheme.colorScheme.onSurface,
                    fieldTextMuted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fieldArrow = MaterialTheme.colorScheme.onSurface,
                    cornerRadius = 8.dp
                )

                Spacer(modifier = Modifier.weight(1f))

                if (hasChanges) {
                    Button(
                        onClick = {
                            mainViewModel.saveFavoriteChoice(selectedCanton, selectedCity)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Sačuvaj", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        if (cantonExpanded || cityExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        cantonExpanded = false
                        cityExpanded = false
                    }
            )
        }
    }
}