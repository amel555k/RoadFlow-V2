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

    var languageExpanded by remember { mutableStateOf(false) }

    val languageOptions = listOf(
        TtsLanguage.BOSNIAN to "Bosanski",
        TtsLanguage.ENGLISH to "Engleski"
    )
    val selectedLanguageLabel = languageOptions.firstOrNull { it.first == ttsLanguage }?.second ?: ""

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (ttsEnabled)
                                MaterialTheme.colorScheme.surface
                            else
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = ttsEnabled) { languageExpanded = !languageExpanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedLanguageLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (ttsEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp),
                        tint = if (ttsEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                DropdownMenu(
                    expanded = languageExpanded && ttsEnabled,
                    onDismissRequest = { languageExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    languageOptions.forEach { (value, text) ->
                        DropdownMenuItem(
                            text = { Text(text, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                soundViewModel.setTtsLanguage(value)
                                languageExpanded = false
                            }
                        )
                    }
                }
            }
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