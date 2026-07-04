package com.amko.roadflow.domain.model

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class RadarData(
    val city: String,
    val time: String,
    val location: String,
    val pageDate: LocalDateTime? = null,
    val coordinate: RadarCoordinate? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedLimit: Int? = null,
    val allCoordinates: List<RadarCoordinate> = emptyList()
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    fun isActiveAt(currentTime: LocalTime): Boolean {
        if (time == "INFO" || time == "GREŠKA") return true
        return try {
            val parts = time.split(" do ")
            if (parts.size != 2) return false

            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val start = LocalTime.parse(parts[0].trim(), formatter)
            val end = LocalTime.parse(parts[1].trim(), formatter)

            !currentTime.isBefore(start) && !currentTime.isAfter(end)
        } catch (e: Exception) {
            false
        }
    }
}