package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.R
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.presentation.viewmodel.MainViewModel
import com.amko.roadflow.presentation.viewmodel.ThemeViewModel
import com.amko.roadflow.ui.theme.AppTheme

@Composable
fun ThemeSettingsScreen(
    themeViewModel: ThemeViewModel,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val currentTheme by themeViewModel.themeMode.collectAsState()
    val currentCanton by mainViewModel.selectedCanton.collectAsState()

    val prefs = mainViewModel.getApplication<android.app.Application>()
        .getSharedPreferences("roadflow_prefs", android.content.Context.MODE_PRIVATE)

    val themes = listOf(
        AppTheme.LIGHT to "Svijetlo",
        AppTheme.DARK to "Tamno",
        AppTheme.SYSTEM to "Sistemski"
    )

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

    val cityList = remember {
        RadarConfig.locations.map { it.name }.distinct().sorted()
    }

    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var selectedCanton by remember {
        mutableStateOf(currentCanton ?: cantonList.first().first)
    }
    var selectedCity by remember {
        mutableStateOf(prefs.getString("favorite_city", cityList.firstOrNull() ?: "") ?: "")
    }

    var themeExpanded by remember { mutableStateOf(false) }
    var cantonExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }

    var saveMessage by remember { mutableStateOf("") }

    val selectedThemeLabel = themes.firstOrNull { it.first == selectedTheme }?.second ?: ""
    val selectedCantonLabel = cantonList.firstOrNull { it.first == selectedCanton }?.second ?: ""

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
            SettingsDropdown(
                label = "Tema aplikacije",
                selectedLabel = selectedThemeLabel,
                options = themes,
                expanded = themeExpanded,
                onExpandedChange = { themeExpanded = it },
                onOptionSelected = { theme ->
                    selectedTheme = theme
                    themeExpanded = false
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsDropdown(
                label = "Omiljeni kanton",
                selectedLabel = selectedCantonLabel,
                options = cantonList,
                expanded = cantonExpanded,
                onExpandedChange = { cantonExpanded = it },
                onOptionSelected = { canton ->
                    selectedCanton = canton
                    cantonExpanded = false
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsDropdown(
                label = "Omiljeni grad",
                selectedLabel = selectedCity,
                options = cityList.map { it to it },
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = it },
                onOptionSelected = { city ->
                    selectedCity = city
                    cityExpanded = false
                }
            )

            if (saveMessage.isNotEmpty()) {
                Text(
                    text = saveMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    themeViewModel.setThemeMode(selectedTheme)
                    mainViewModel.saveFavoriteChoice(selectedCanton, selectedCity)
                    saveMessage = "Spremljeno!"
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Sačuvaj", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun <T> SettingsDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (T) -> Unit
) {
    Column {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onExpandedChange(!expanded) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .heightIn(max = 400.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { onOptionSelected(value) }
                )
            }
        }
    }
}