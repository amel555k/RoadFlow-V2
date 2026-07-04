package com.amko.roadflow.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.NoInternetWithCacheException
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.data.local.RadarParser
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

sealed class RadarListItem {
    data class CityHeader(val city: String) : RadarListItem()
    data class RadarEntry(val radar: RadarData) : RadarListItem()
    data class Spacer(val id: String) : RadarListItem()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseService = FirebaseService()
    private val parser = RadarParser(application, firebaseService)

    private val _allRadars = MutableStateFlow<List<RadarData>>(emptyList())
    private val _displayedRadars = MutableStateFlow<List<RadarData>>(emptyList())
    val displayedRadars: StateFlow<List<RadarData>> = _displayedRadars

    val isLoading = MutableStateFlow(true)
    val showNoInternet = MutableStateFlow(false)
    val selectedCanton = MutableStateFlow<Canton?>(Canton.Srednjobosanski)
    val currentDate = MutableStateFlow(LocalDate.now())

    private val _uiList = MutableStateFlow<List<RadarListItem>>(emptyList())
    val uiList: StateFlow<List<RadarListItem>> = _uiList
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

    init {
        viewModelScope.launch {
            isLoading.collect { android.util.Log.d("ROADFLOW", "isLoading=$it uiList.size=${_uiList.value.size}") }
        }
        loadData()
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

    fun loadData() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                var firstEmit = true
                parser.parseAllLocationsAsFlow().collect { partialRadars ->
                    _allRadars.value = partialRadars
                    parser.updateCache(partialRadars)

                    if (firstEmit) {
                        val filtered = withContext(Dispatchers.Default) {
                            filterForCanton(partialRadars, selectedCanton.value)
                        }
                        _displayedRadars.value = filtered
                        _uiList.value = buildUiList(filtered)
                        currentDate.value = LocalDate.now()
                        isLoading.value = false
                        firstEmit = false
                    } else {
                        filteringJob?.cancel()

                        filteringJob = viewModelScope.launch(Dispatchers.Default) {
                            val filtered = filterForCanton(partialRadars, selectedCanton.value)
                            if (filtered != _displayedRadars.value) {
                                withContext(Dispatchers.Main) {
                                    _displayedRadars.value = filtered
                                    _uiList.value = buildUiList(filtered)
                                }
                            }
                        }
                    }
                }
            } catch (e: NoInternetWithCacheException) {
                _allRadars.value = e.cachedRadars
                parser.updateCache(e.cachedRadars)

                filteringJob?.cancel()
                filteringJob = viewModelScope.launch(Dispatchers.Default) {
                    val filtered = filterForCanton(e.cachedRadars, selectedCanton.value)
                    withContext(Dispatchers.Main) {
                        _displayedRadars.value = filtered
                        _uiList.value = buildUiList(filtered)
                    }
                }

                currentDate.value = LocalDate.now()
                showNoInternet.value = true
                isLoading.value = false
            } catch (_: Exception) {
                showNoInternet.value = true
                isLoading.value = false
            }
        }
    }
}