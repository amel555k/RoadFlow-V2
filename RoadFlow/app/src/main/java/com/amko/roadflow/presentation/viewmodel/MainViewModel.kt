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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RadarListItem {
    data class CityHeader(val city: String) : RadarListItem()
    data class RadarEntry(val radar: RadarData) : RadarListItem()
    data class Spacer(val id: String) : RadarListItem()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseService = FirebaseService()
    private val parser = RadarParser(application, firebaseService)

    private val prefs = application.getSharedPreferences("roadflow_prefs", Application.MODE_PRIVATE)

    private val _allRadars = MutableStateFlow<List<RadarData>>(emptyList())
    private val _displayedRadars = MutableStateFlow<List<RadarData>>(emptyList())
    val displayedRadars: StateFlow<List<RadarData>> = _displayedRadars

    val isLoading = MutableStateFlow(true)
    val showNoInternet = MutableStateFlow(false)
    val isRefreshing = MutableStateFlow(false)
    val refreshSuccessEvent = MutableStateFlow(0L)
    val hasError = MutableStateFlow(false)

    private val savedCantonName = prefs.getString("favorite_canton", null)
    private val initialCanton = savedCantonName?.let { name ->
        Canton.entries.firstOrNull { it.name == name }
    }
    val selectedCanton = MutableStateFlow(initialCanton)
    val currentDate = MutableStateFlow(com.amko.roadflow.data.local.TimeProvider.effectiveRadarDate())

    val canPullToRefresh = MutableStateFlow(true)

    val notificationEnabled = MutableStateFlow(prefs.getBoolean("notification_enabled", true))

    fun setNotificationEnabled(enabled: Boolean) {
        notificationEnabled.value = enabled
        prefs.edit().putBoolean("notification_enabled", enabled).apply()

        if (enabled) {
            com.amko.roadflow.data.local.RadarWorkScheduler.schedulePeriodic(getApplication())
            com.amko.roadflow.data.local.RadarWorkScheduler.scheduleOneTime(getApplication())
        } else {
            com.amko.roadflow.data.local.RadarWorkScheduler.cancelAll(getApplication())
            val notificationManager = getApplication<Application>().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(1001)
        }
    }

    fun saveFavoriteChoice(canton: Canton, city: String) {
        prefs.edit()
            .putString("favorite_canton", canton.name)
            .putString("favorite_city", city)
            .apply()

        android.util.Log.d("ROADFLOW1", "saveFavoriteChoice: canton=$canton allRadars.size=${_allRadars.value.size}")

        val filtered = filterForCanton(_allRadars.value, canton)
        android.util.Log.d("ROADFLOW1", "saveFavoriteChoice: filtered.size=${filtered.size}")

        _displayedRadars.value = filtered
        _uiList.value = buildUiList(filtered)
        selectedCanton.value = canton

        android.util.Log.d("ROADFLOW1", "saveFavoriteChoice: uiList.size=${_uiList.value.size}")

        if (prefs.getBoolean("notification_enabled", false)) {
            com.amko.roadflow.data.local.RadarWorkScheduler.cancelAll(getApplication())
            com.amko.roadflow.data.local.RadarWorkScheduler.scheduleOneTime(getApplication())
            com.amko.roadflow.data.local.RadarWorkScheduler.schedulePeriodic(getApplication())
        }
    }

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
            isLoading.collect { android.util.Log.d("ROADFLOW1", "isLoading=$it uiList.size=${_uiList.value.size}") }
        }
        viewModelScope.launch {
            currentDate.value = com.amko.roadflow.data.local.TimeProvider.effectiveRadarDate()
            loadData()
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

    fun refreshData() {
        if (isRefreshing.value) return

        viewModelScope.launch {
            isRefreshing.value = true
            loadDataInternal(forceRefresh = true)
        }
    }

    fun loadData() {
        viewModelScope.launch {
            isLoading.value = true

            val lastSeenDate = prefs.getString("last_seen_radar_date", null)
            val todayStr = com.amko.roadflow.data.local.TimeProvider.effectiveRadarDate().toString()
            val isNewDay = lastSeenDate != todayStr

            loadDataInternal(forceRefresh = isNewDay)
            prefs.edit().putString("last_seen_radar_date", todayStr).apply()

            val cachedToday = withContext(Dispatchers.IO) { parser.isCachedForToday() }
            canPullToRefresh.value = !cachedToday
        }
    }

    private suspend fun loadDataInternal(forceRefresh: Boolean = false) {
        try {
            hasError.value = false
            var firstEmit = true
            var priorityHandled = false
            parser.parseAllLocationsAsFlow(favoriteCanton = selectedCanton.value, forceRefresh = forceRefresh).collect { progress ->
                val partialRadars = progress.radars
                android.util.Log.d("ROADFLOW1", "loadData emit: size=${partialRadars.size} firstEmit=$firstEmit priorityComplete=${progress.priorityCantonComplete} selectedCanton=${selectedCanton.value}")

                _allRadars.value = partialRadars
                parser.updateCache(partialRadars)

                if (firstEmit) {
                    val filtered = withContext(Dispatchers.Default) {
                        filterForCanton(partialRadars, selectedCanton.value)
                    }
                    android.util.Log.d("ROADFLOW1", "loadData firstEmit filtered.size=${filtered.size}")
                    _displayedRadars.value = filtered
                    _uiList.value = buildUiList(filtered)
                    currentDate.value = com.amko.roadflow.data.local.TimeProvider.effectiveRadarDate()
                    isLoading.value = false
                    firstEmit = false
                } else {
                    filteringJob?.cancel()

                    filteringJob = viewModelScope.launch(Dispatchers.Default) {
                        val filtered = filterForCanton(partialRadars, selectedCanton.value)
                        android.util.Log.d("ROADFLOW1", "loadData laterEmit filtered.size=${filtered.size} equalsCurrentDisplayed=${filtered == _displayedRadars.value}")
                        if (filtered != _displayedRadars.value) {
                            withContext(Dispatchers.Main) {
                                _displayedRadars.value = filtered
                                _uiList.value = buildUiList(filtered)
                                android.util.Log.d("ROADFLOW1", "loadData laterEmit APPLIED uiList.size=${_uiList.value.size}")
                            }
                        }
                    }
                }

                if (progress.priorityCantonComplete && !priorityHandled) {
                    priorityHandled = true
                    isRefreshing.value = false
                    if (forceRefresh) {
                        refreshSuccessEvent.value = System.currentTimeMillis()
                    }
                }
            }

            if (!priorityHandled) {
                isRefreshing.value = false
                if (forceRefresh) {
                    refreshSuccessEvent.value = System.currentTimeMillis()
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
            currentDate.value = com.amko.roadflow.data.local.TimeProvider.effectiveRadarDate()
            showNoInternet.value = true
            isLoading.value = false
            isRefreshing.value = false
        } catch (_: Exception) {
            showNoInternet.value = true
            hasError.value = true
            isLoading.value = false
            isRefreshing.value = false
        }
    }
}