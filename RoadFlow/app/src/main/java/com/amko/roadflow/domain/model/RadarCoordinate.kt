package com.amko.roadflow.domain.model

data class RadarCoordinate(
    val mainName: String,
    val latitude: Double,
    val longitude: Double,
    val speedLimit: Int,
    val startTime: String? = null,
    val endTime: String? = null,
    val stacionaran: Boolean = false
)