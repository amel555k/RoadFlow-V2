package com.amko.roadflow.domain.model

data class RadarLocation(
    val name: String,
    val possibleIds: List<Int>,
    val canton: Canton,
    val mapEnabled: Boolean = false,
    val fromFirebase: Boolean = false
)