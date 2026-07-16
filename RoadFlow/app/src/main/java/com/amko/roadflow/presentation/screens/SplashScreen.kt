package com.amko.roadflow.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.amko.roadflow.domain.model.Canton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun SplashScreen(
    cantonList: List<Pair<Canton, String>>,
    cityToCanton: List<Pair<String, Canton>>,
    onSave: (Canton, String) -> Unit
) {
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
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1A2B),
                        Color(0xFF16273D),
                        Color(0xFF3C7EA8)
                    )
                )
            )
    ) {
        Text(
            text = "RoadFlow",
            fontSize = 42.sp,
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
                text = "Odaberite omiljeni kanton",
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            cantonRowWidth = with(density) { coordinates.size.width.toDp() }
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(fieldBackground)
                        .clickable {
                            val opening = !isCantonDropdownOpen
                            isCantonDropdownOpen = opening
                            isCityDropdownOpen = false
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = cantonList.firstOrNull { it.first == selectedCanton }?.second
                            ?: "Odaberite kanton",
                        fontSize = 14.sp,
                        color = if (selectedCanton != null) fieldText else fieldTextMuted
                    )
                    Text(
                        text = if (isCantonDropdownOpen) "▲" else "▼",
                        fontSize = 12.sp,
                        color = fieldArrow
                    )
                }

                if (isCantonDropdownOpen) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(0, 130)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(cantonRowWidth)
                                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                                .background(fieldBackground)
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            cantonList.forEach { (canton, label) ->
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    color = fieldText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (canton != selectedCanton) {
                                                selectedCity = null
                                            }
                                            selectedCanton = canton
                                            isCantonDropdownOpen = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Odaberite omiljeni grad",
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            cityRowWidth = with(density) { coordinates.size.width.toDp() }
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(fieldBackground)
                        .clickable {
                            if (selectedCanton == null) {
                                coroutineScope.launch { cantonShakeOffset.shake() }
                            } else {
                                val opening = !isCityDropdownOpen
                                isCityDropdownOpen = opening
                                isCantonDropdownOpen = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedCity ?: "Odaberite grad",
                        fontSize = 14.sp,
                        color = if (selectedCity != null) fieldText else fieldTextMuted
                    )
                    Text(
                        text = if (isCityDropdownOpen) "▲" else "▼",
                        fontSize = 12.sp,
                        color = fieldArrow
                    )
                }

                if (isCityDropdownOpen) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(0, 130)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(cityRowWidth)
                                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                                .background(fieldBackground)
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            cityList.forEach { city ->
                                Text(
                                    text = city,
                                    fontSize = 14.sp,
                                    color = fieldText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCity = city
                                            isCityDropdownOpen = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val canton = selectedCanton
                val city = selectedCity

                if (canton == null || city == null) {
                    coroutineScope.launch {
                        if (canton == null) launch { cantonShakeOffset.shake() }
                        if (city == null) launch { cityShakeOffset.shake() }
                    }
                } else {
                    onSave(canton, city)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = buttonBackground),
            modifier = Modifier
                .align(Alignment.BottomCenter)
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