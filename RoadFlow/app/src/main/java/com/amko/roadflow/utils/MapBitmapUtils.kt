package com.amko.roadflow.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.amko.roadflow.R

fun createCircleFeature(
    lng: Double,
    lat: Double,
    radiusMeters: Double,
    points: Int = 64
): org.maplibre.geojson.Feature {
    val km = radiusMeters / 1000.0
    val distanceX = km / (111.320 * Math.cos(Math.toRadians(lat)))
    val distanceY = km / 110.574

    val coordinates = mutableListOf<org.maplibre.geojson.Point>()
    for (i in 0 until points) {
        val theta = (i.toDouble() / points) * (2 * Math.PI)
        val x = distanceX * Math.cos(theta)
        val y = distanceY * Math.sin(theta)
        coordinates.add(org.maplibre.geojson.Point.fromLngLat(lng + x, lat + y))
    }
    coordinates.add(coordinates[0])

    return org.maplibre.geojson.Feature.fromGeometry(
        org.maplibre.geojson.Polygon.fromLngLats(listOf(coordinates))
    )
}

fun createUserBitmap(context: android.content.Context): Bitmap {
    val drawable = context.getDrawable(R.drawable.user_marker)
    if (drawable != null) {
        val size = 80
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

    val size = 60
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, bgPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, borderPaint)

    val path = android.graphics.Path()
    path.moveTo(size / 2f, 8f)
    path.lineTo(size / 2f - 10f, size / 2f + 10f)
    path.lineTo(size / 2f, size / 2f)
    path.lineTo(size / 2f + 10f, size / 2f + 10f)
    path.close()
    canvas.drawPath(path, arrowPaint)

    return bmp
}
fun createRadarBitmap(context: android.content.Context, isStacionarni: Boolean): Bitmap {
    val drawableId = if (isStacionarni) R.drawable.stac_radar else R.drawable.active_radar
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId)

    if (drawable != null) {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

    val size = 60
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bgColor = if (isStacionarni) Color.parseColor("#1565C0") else Color.parseColor("#1E88E5")
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, bgPaint)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, borderPaint)
    return bmp
}