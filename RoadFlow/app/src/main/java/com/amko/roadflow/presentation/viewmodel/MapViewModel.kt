package com.amko.roadflow.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amko.roadflow.data.local.RadarConfig
import com.amko.roadflow.data.local.RadarParser
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.LocationTrackingService
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseService = FirebaseService()
    private val parser = RadarParser(application, firebaseService)

    val locationService = LocationTrackingService(application)

    private val _activeRadars = MutableStateFlow<List<RadarData>>(emptyList())
    val activeRadars: StateFlow<List<RadarData>> = _activeRadars

    private val _allRadars = MutableStateFlow<List<RadarData>>(emptyList())
    val allRadars: StateFlow<List<RadarData>> = _allRadars

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadRadars()
    }

    fun loadRadars() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val all = parser.parseAllLocationsAsync()
                _allRadars.value = all

                val now = LocalTime.now()
                val active = all.filter { radar ->
                    radar.time != "INFO" && isActiveNow(radar.time, now)
                }

                val stacionarni = RadarConfig.coordinates
                    .filter { it.stacionaran }
                    .map { coord ->
                        RadarData(
                            city = coord.mainName,
                            time = "00:00 do 24:00",
                            location = coord.mainName,
                            latitude = coord.latitude,
                            longitude = coord.longitude,
                            speedLimit = coord.speedLimit
                        )
                    }

                _activeRadars.value = active + stacionarni
            } catch (e: Exception) {

            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun isActiveNow(timeRange: String, now: LocalTime): Boolean {
        return try {
            val parts = timeRange.split(" do ")
            if (parts.size != 2) return false
            val fmt = DateTimeFormatter.ofPattern("H:mm")
            val start = LocalTime.parse(parts[0].trim(), fmt)
            val end = LocalTime.parse(parts[1].trim(), fmt)
            !now.isBefore(start) && !now.isAfter(end)
        } catch (e: Exception) {
            false
        }
    }

    enum class RadarFilter { ACTIVE, TODAY }

    fun setFilter(filter: RadarFilter) {
        viewModelScope.launch {
            val now = LocalTime.now()
            _activeRadars.value = when (filter) {
                RadarFilter.ACTIVE -> _allRadars.value.filter {
                    it.time != "INFO" && isActiveNow(it.time, now)
                } + getStacionarni()
                RadarFilter.TODAY -> _allRadars.value.filter {
                    it.time != "INFO"
                } + getStacionarni()
            }
        }
    }

    private fun getStacionarni() = RadarConfig.coordinates
        .filter { it.stacionaran }
        .map { coord ->
            RadarData(
                city = coord.mainName,
                time = "00:00 do 24:00",
                location = coord.mainName,
                latitude = coord.latitude,
                longitude = coord.longitude,
                speedLimit = coord.speedLimit
            )
        }

    override fun onCleared() {
        super.onCleared()
        locationService.dispose()
    }
}