package com.amko.roadflow.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.presentation.components.AppDropdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    cantonList: List<Pair<Canton, String>>,
    cityToCanton: List<Pair<String, Canton>>,
    onSave: (Canton, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current

    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (context as android.app.Activity).window
        val originalStatusBarColor = window.statusBarColor
        val originalLightStatusBars = androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars

        window.statusBarColor = 0xFF0E1A2B.toInt()
        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false

        onDispose {
            window.statusBarColor = originalStatusBarColor
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = originalLightStatusBars
        }
    }

    var selectedCanton by remember { mutableStateOf<Canton?>(null) }
    var selectedCity by remember { mutableStateOf<String?>(null) }
    var isCantonDropdownOpen by remember { mutableStateOf(false) }
    var isCityDropdownOpen by remember { mutableStateOf(false) }

    val cantonShakeOffset = remember { Animatable(0f) }
    val cityShakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun Animatable<Float, *>.shake() {
        val keyframes = listOf(0f, -20f, 20f, -14f, 14f, -8f, 8f, 0f)
        for (target in keyframes) {
            animateTo(target, animationSpec = tween(durationMillis = 40))
        }
    }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(0f) }
    val formAlpha = remember { Animatable(0f) }
    val formOffsetY = remember { Animatable(80f) }
    val density = LocalDensity.current
    var cantonRowWidth by remember { mutableStateOf(0.dp) }
    var cityRowWidth by remember { mutableStateOf(0.dp) }

    val fieldBackground = Color(0xFF0E1A2B)
    val fieldText = Color(0xFFE4ECF5)
    val fieldTextMuted = Color(0xFFE4ECF5).copy(alpha = 0.5f)
    val fieldArrow = Color(0xFFE4ECF5).copy(alpha = 0.6f)
    val buttonBackground = Color(0xFF0E1A2B)
    val buttonText = Color(0xFFFFFFFF)

    val cityList = remember(selectedCanton) {
        cityToCanton
            .filter { it.second == selectedCanton }
            .map { it.first }
            .distinct()
            .sorted()
    }

    LaunchedEffect(Unit) {
        titleAlpha.animateTo(1f, animationSpec = tween(durationMillis = 600))
        delay(500)
        titleOffsetY.animateTo(-750f, animationSpec = tween(durationMillis = 700, easing = EaseOutCubic))
        launch {
            formAlpha.animateTo(1f, animationSpec = tween(durationMillis = 600))
        }
        formOffsetY.animateTo(0f, animationSpec = tween(durationMillis = 700, easing = EaseOutCubic))
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.amko.roadflow.R.drawable.splash_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        Text(
            text = "RoadFlow",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffsetY.value
                }
        )

        if (isCantonDropdownOpen || isCityDropdownOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        isCantonDropdownOpen = false
                        isCityDropdownOpen = false
                    }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(24.dp)
                .graphicsLayer {
                    alpha = formAlpha.value
                    translationY = formOffsetY.value
                }
        ) {
            Text(
                text = "Odaberite vaš kanton",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = cantonShakeOffset.value }
            ) {
                AppDropdown(
                    options = cantonList,
                    selectedLabel = cantonList.firstOrNull { it.first == selectedCanton }?.second ?: "",
                    selectedValue = selectedCanton,
                    placeholder = "Odaberite kanton",
                    expanded = isCantonDropdownOpen,
                    onExpandedChange = { opening ->
                        isCantonDropdownOpen = opening
                        if (opening) isCityDropdownOpen = false
                    },
                    onOptionSelected = { canton ->
                        if (canton != selectedCanton) {
                            selectedCity = null
                        }
                        selectedCanton = canton
                        isCantonDropdownOpen = false
                    },
                    fieldBackground = fieldBackground,
                    fieldText = fieldText,
                    fieldTextMuted = fieldTextMuted,
                    fieldArrow = fieldArrow
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Odaberite vaš grad",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = cityShakeOffset.value }
            ) {
                AppDropdown(
                    options = cityList.map { it to it },
                    selectedLabel = selectedCity ?: "",
                    selectedValue = selectedCity,
                    placeholder = "Odaberite grad",
                    expanded = isCityDropdownOpen,
                    onExpandedChange = { opening ->
                        if (selectedCanton == null) {
                            coroutineScope.launch { cantonShakeOffset.shake() }
                        } else {
                            isCityDropdownOpen = opening
                            if (opening) isCantonDropdownOpen = false
                        }
                    },
                    onOptionSelected = { city ->
                        selectedCity = city
                        isCityDropdownOpen = false
                    },
                    fieldBackground = fieldBackground,
                    fieldText = fieldText,
                    fieldTextMuted = fieldTextMuted,
                    fieldArrow = fieldArrow
                )
            }
        }

        AnimatedVisibility(
            visible = selectedCanton != null && selectedCity != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = fadeOut(animationSpec = tween(durationMillis = 300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Button(
                onClick = {
                    val canton = selectedCanton
                    val city = selectedCity

                    if (canton != null && city != null) {
                        onSave(canton, city)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = buttonBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .graphicsLayer {
                        alpha = formAlpha.value
                        translationY = -formOffsetY.value
                    }
            ) {
                Text(
                    text = "SAČUVAJ",
                    color = buttonText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}