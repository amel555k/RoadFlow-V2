package com.amko.roadflow.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.R
import com.amko.roadflow.presentation.components.AppDropdown
import com.amko.roadflow.presentation.viewmodel.SoundViewModel
import com.amko.roadflow.presentation.viewmodel.TtsLanguage

@Composable
fun SoundSettingsScreen(
    soundViewModel: SoundViewModel,
    onBack: () -> Unit
) {
    val vibrationEnabled by soundViewModel.vibrationEnabled.collectAsState()
    val ttsEnabled by soundViewModel.ttsEnabled.collectAsState()
    val ttsLanguage by soundViewModel.ttsLanguage.collectAsState()
    val alertRadius by soundViewModel.alertRadius.collectAsState()

    var languageExpanded by remember { mutableStateOf(false) }
    var radiusExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = languageExpanded || radiusExpanded) {
        languageExpanded = false
        radiusExpanded = false
    }

    val languageOptions = remember {
        listOf(
            TtsLanguage.BOSNIAN to "Bosanski",
            TtsLanguage.ENGLISH to "Engleski"
        )
    }

    val filteredLanguageOptions = remember(ttsLanguage) {
        languageOptions.filter { it.first != ttsLanguage }
    }

    val selectedLanguageLabel =
        languageOptions.firstOrNull { it.first == ttsLanguage }?.second ?: ""

    val radiusOptions = remember {
        listOf(
            100 to "100 m",
            150 to "150 m",
            200 to "200 m",
            250 to "250 m"
        )
    }

    val selectedRadiusLabel =
        radiusOptions.firstOrNull { it.first == alertRadius }?.second ?: ""
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
                    text = "Postavke signalizacije",
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

                Spacer(modifier = Modifier.height(24.dp))

                ToggleRow(
                    title = "Vibracija",
                    description = "Uređaj zavibrira pri ulasku u zonu radara i tokom signalizacije.",
                    checked = vibrationEnabled,
                    onCheckedChange = { soundViewModel.setVibrationEnabled(it) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                ToggleRow(
                    title = "Govor",
                    description = "Glasovno obavještenje o vrsti kamere i ograničenju brzine.",
                    checked = ttsEnabled,
                    onCheckedChange = { soundViewModel.setTtsEnabled(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column {
                    Text(
                        text = "Jezik govora:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = if (ttsEnabled)
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )

                    AppDropdown(
                        options = filteredLanguageOptions,
                        selectedLabel = selectedLanguageLabel,
                        selectedValue = ttsLanguage,
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it },
                        onOptionSelected = { lang ->
                            soundViewModel.setTtsLanguage(lang)
                            languageExpanded = false
                        },
                        fieldBackground = if (ttsEnabled)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        fieldText = if (ttsEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fieldTextMuted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fieldArrow = if (ttsEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        enabled = ttsEnabled,
                        cornerRadius = 8.dp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column {
                    Text(
                        text = "Radijus obavještenja:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    AppDropdown(
                        options = radiusOptions,
                        selectedLabel = selectedRadiusLabel,
                        selectedValue = alertRadius,
                        expanded = radiusExpanded,
                        onExpandedChange = { radiusExpanded = it },
                        onOptionSelected = { radius ->
                            soundViewModel.setAlertRadius(radius)
                            radiusExpanded = false
                        },
                        fieldBackground = MaterialTheme.colorScheme.surface,
                        fieldText = MaterialTheme.colorScheme.onSurface,
                        fieldTextMuted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fieldArrow = MaterialTheme.colorScheme.onSurface,
                        enabled = true,
                        cornerRadius = 8.dp
                    )
                }
            }
        }

        if (languageExpanded || radiusExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        languageExpanded = false
                        radiusExpanded = false
                    }
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}