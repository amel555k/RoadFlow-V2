package com.amko.roadflow.data.local

object MockRouteData {
    fun getMockRoute(): RouteResult {
        val rawCoordinates = listOf(

        )

        return RouteResult(
            distanceMeters = 21193.2,
            durationSeconds = 1523.8,
            coordinates = rawCoordinates
        )
    }
}