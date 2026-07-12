package com.amko.roadflow.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseService = FirebaseService()

    private val prefs = application.getSharedPreferences("roadflow_prefs", Application.MODE_PRIVATE)

    private val _allRadars = MutableStateFlow<List<RadarData>>(emptyList())
    private val _displayedRadars = MutableStateFlow<List<RadarData>>(emptyList())
    val displayedRadars: StateFlow<List<RadarData>> = _displayedRadars

    val isLoading = MutableStateFlow(false)
    val showNoInternet = MutableStateFlow(false)

    private val savedCantonName = prefs.getString("favorite_canton", null)
    private val initialCanton = savedCantonName?.let { name ->
        Canton.entries.firstOrNull { it.name == name }
    }
    val selectedCanton = MutableStateFlow(initialCanton)
    val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val _uiList = MutableStateFlow<List<RadarListItem>>(emptyList())
    val uiList: StateFlow<List<RadarListItem>> = _uiList

    private var loadingJob: Job? = null
    private var filteringJob: Job? = null

    private fun buildUiList(radars: List<RadarData>): List<RadarListItem> {
        val grouped = radars.groupBy { it.city }
        return buildList {
            grouped.forEach { (city, cityRadars) ->
                add(RadarListItem.CityHeader(city))
                cityRadars.forEach { radar -> add(RadarListItem.RadarEntry(radar)) }
                add(RadarListItem.Spacer(id = "spacer_$city"))
            }
        }
    }

    fun filterForCanton(all: List<RadarData>, canton: Canton?): List<RadarData> {
        if (canton == null) return all
        val cities = RadarConfig.locations
            .filter { it.canton == canton }
            .map { it.name }
            .toHashSet()
        return all.filter { cities.contains(it.city) }
    }

    fun selectCanton(canton: Canton?) {
        selectedCanton.value = canton

        filteringJob?.cancel()
        filteringJob = viewModelScope.launch(Dispatchers.Default) {
            val filtered = filterForCanton(_allRadars.value, canton)
            withContext(Dispatchers.Main) {
                _displayedRadars.value = filtered
                _uiList.value = buildUiList(filtered)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date

        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            isLoading.value = true
            showNoInternet.value = false
            try {
                com.amko.roadflow.data.local.TimeProvider.awaitFirstSync()
                val radars = firebaseService.getHistoryRadarsAsync(date)
                _allRadars.value = radars

                val filtered = withContext(Dispatchers.Default) {
                    filterForCanton(radars, selectedCanton.value)
                }
                _displayedRadars.value = filtered
                _uiList.value = buildUiList(filtered)
            } catch (e: Exception) {
                _allRadars.value = emptyList()
                _displayedRadars.value = emptyList()
                _uiList.value = emptyList()
                showNoInternet.value = true
            } finally {
                isLoading.value = false
            }
        }
    }

    fun clearSelection() {
        loadingJob?.cancel()
        filteringJob?.cancel()
        selectedDate.value = null
        _allRadars.value = emptyList()
        _displayedRadars.value = emptyList()
        _uiList.value = emptyList()
        isLoading.value = false
        showNoInternet.value = false
    }
}