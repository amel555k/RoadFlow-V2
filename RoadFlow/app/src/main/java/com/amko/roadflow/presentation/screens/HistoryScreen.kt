package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.presentation.components.BottomNavBar
import com.amko.roadflow.presentation.components.NoConnectionDialog
import com.amko.roadflow.presentation.viewmodel.HistoryViewModel
import com.amko.roadflow.presentation.viewmodel.RadarListItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.amko.roadflow.presentation.components.RadarItem
import com.amko.roadflow.presentation.components.CantonPickerDropdown

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val uiList by viewModel.uiList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedCanton by viewModel.selectedCanton.collectAsState()
    val showNoInternet by viewModel.showNoInternet.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var isDropdownOpen by remember { mutableStateOf(false) }
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDayOverlay by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    LaunchedEffect(Unit) {
        if (selectedCanton == null) {
            viewModel.selectCanton(Canton.Srednjobosanski)
        }
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

    val selectedCantonLabel = cantonList.firstOrNull { it.first == selectedCanton }?.second ?: "Srednjobosanski kanton"

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        brush = if (isDark) {
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        } else {
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0E1A2B),
                                    Color(0xFF16273D),
                                    Color(0xFF3C7EA8)
                                )
                            )
                        }
                    )
                    .statusBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CalendarView(
                        displayedMonth = displayedMonth,
                        selectedDate = selectedDate,
                        today = today,
                        arrowsEnabled = !showDayOverlay,
                        isDark = isDark,
                        onPrevMonth = { displayedMonth = displayedMonth.minusMonths(1) },
                        onNextMonth = { displayedMonth = displayedMonth.plusMonths(1) },
                        onDayClick = { date ->
                            viewModel.selectDate(date)
                            showDayOverlay = true
                        }
                    )
                }
            }

            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate
            )
        }

        if (showDayOverlay) {
            DayDetailsOverlay(
                selectedDate = selectedDate,
                uiList = uiList,
                isLoading = isLoading,
                selectedCanton = selectedCanton,
                selectedCantonLabel = selectedCantonLabel,
                cantonList = cantonList,
                isDropdownOpen = isDropdownOpen,
                onDropdownToggle = { isDropdownOpen = !isDropdownOpen },
                onCantonSelected = { canton ->
                    viewModel.selectCanton(canton)
                    isDropdownOpen = false
                },
                onDismissDropdown = { isDropdownOpen = false },
                onClose = {
                    showDayOverlay = false
                    isDropdownOpen = false
                }
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

@Composable
private fun DayDetailsOverlay(
    selectedDate: LocalDate?,
    uiList: List<RadarListItem>,
    isLoading: Boolean,
    selectedCanton: Canton?,
    selectedCantonLabel: String,
    cantonList: List<Pair<Canton, String>>,
    isDropdownOpen: Boolean,
    onDropdownToggle: () -> Unit,
    onCantonSelected: (Canton?) -> Unit,
    onDismissDropdown: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {

        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Nazad",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clickable { onClose() }
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = selectedDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondary)
                    .clickable { onDropdownToggle() }
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (uiList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nema radara za odabrani dan.",
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
                        items = uiList,
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

        if (isDropdownOpen) {
            CantonPickerDropdown(
                cantonList = cantonList,
                selectedCanton = selectedCanton,
                onCantonSelected = onCantonSelected,
                onDismiss = onDismissDropdown
            )
        }
    }
}

@Composable
private fun CalendarView(
    displayedMonth: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    arrowsEnabled: Boolean,
    isDark: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable(enabled = arrowsEnabled) { onPrevMonth() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Prethodni mjesec",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = displayedMonth.month.getDisplayName(TextStyle.FULL, Locale("bs"))
                        .replaceFirstChar { it.uppercase() } + " ${displayedMonth.year}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable(enabled = arrowsEnabled) { onNextMonth() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Sljedeći mjesec",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val dayLabels = listOf("Pon", "Uto", "Sri", "Čet", "Pet", "Sub", "Ned")
            Row(modifier = Modifier.fillMaxWidth()) {
                dayLabels.forEach { label ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val firstOfMonth = displayedMonth.atDay(1)
            val leadingEmptyDays = (firstOfMonth.dayOfWeek.value + 6) % 7
            val daysInMonth = displayedMonth.lengthOfMonth()
            val totalCells = leadingEmptyDays + daysInMonth
            val trailingEmptyDays = (7 - (totalCells % 7)) % 7

            val cells: List<LocalDate?> = buildList {
                repeat(leadingEmptyDays) { add(null) }
                for (day in 1..daysInMonth) { add(displayedMonth.atDay(day)) }
                repeat(trailingEmptyDays) { add(null) }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.heightIn(max = 280.dp),
                userScrollEnabled = false
            ) {
                items(cells) { date ->
                    if (date == null) {
                        Box(modifier = Modifier.aspectRatio(1f))
                    } else {
                        val isToday = date == today
                        val isSelected = date == selectedDate
                        val backgroundColor = when {
                            isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> Color.Transparent
                        }
                        val textColor = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(backgroundColor)
                                .clickable(enabled = arrowsEnabled) { onDayClick(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}