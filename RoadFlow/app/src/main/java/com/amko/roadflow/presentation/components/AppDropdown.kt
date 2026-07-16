package com.amko.roadflow.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.material3.Text

@Composable
fun <T> AppDropdown(
    options: List<Pair<T, String>>,
    selectedLabel: String,
    selectedValue: T? = null,
    placeholder: String = "",
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (T) -> Unit,
    fieldBackground: Color,
    fieldText: Color,
    fieldTextMuted: Color,
    fieldArrow: Color,
    selectedItemBackground: Color = fieldArrow.copy(alpha = 0.15f),
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp,
    fontSize: TextUnit = 14.sp,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 14.dp,
    maxDropdownHeight: Dp = 220.dp
) {
    val density = LocalDensity.current
    var rowWidth by remember { mutableStateOf(0.dp) }
    val hasSelection = selectedLabel.isNotEmpty()

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    rowWidth = with(density) { coordinates.size.width.toDp() }
                }
                .clip(RoundedCornerShape(cornerRadius))
                .background(fieldBackground)
                .clickable(enabled = enabled) { onExpandedChange(!expanded) }
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (hasSelection) selectedLabel else placeholder,
                fontSize = fontSize,
                modifier = Modifier.weight(1f),
                color = if (hasSelection) fieldText else fieldTextMuted
            )
            Text(
                text = if (expanded) "▲" else "▼",
                fontSize = fontSize * 0.85f,
                color = fieldArrow
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(density) {
                    (verticalPadding * 2 + fontSize.value.dp).roundToPx()
                })
            ) {
                Column(
                    modifier = Modifier
                        .width(rowWidth)
                        .clip(
                            RoundedCornerShape(
                                topStart = 0.dp,
                                topEnd = 0.dp,
                                bottomStart = cornerRadius,
                                bottomEnd = cornerRadius
                            )
                        )
                        .background(fieldBackground)
                        .heightIn(max = maxDropdownHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEach { (value, text) ->
                        val isSelected = value == selectedValue
                        Text(
                            text = text,
                            fontSize = fontSize,
                            color = fieldText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) selectedItemBackground else Color.Transparent)
                                .clickable { onOptionSelected(value) }
                                .padding(horizontal = horizontalPadding, vertical = verticalPadding * 0.85f)
                        )
                    }
                }
            }
        }
    }
}