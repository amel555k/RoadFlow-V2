package com.amko.roadflow.presentation.screens

import android.graphics.Color
import com.amko.roadflow.utils.createRadarBitmap
import com.amko.roadflow.utils.createUserBitmap
import org.maplibre.geojson.FeatureCollection

fun setupMapStyle(
    context: android.content.Context,
    style: org.maplibre.android.maps.Style,
    radarIconId: String,
    radarIconStacionarniId: String,
    userIconId: String,
    destinationIconId: String,
    radarSourceId: String,
    radarLayerId: String,
    userSourceId: String,
    userLayerId: String,
    destinationSourceId: String,
    destinationLayerId: String,
    routeAltSourceId: String,
    routeAltLayerId: String,
    routeAltHitareaLayerId: String,
    routeLabelSourceId: String,
    routeLabelLayerId: String,
    routeAltLabelSourceId: String,
    routeAltLabelLayerId: String
) {
    style.addImage(radarIconId, createRadarBitmap(context, false))
    style.addImage(radarIconStacionarniId, createRadarBitmap(context, true))
    style.addImage(userIconId, createUserBitmap(context))
    style.addImage(destinationIconId, createDestinationBitmap(context))

    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            "radar-zones-source",
            FeatureCollection.fromFeatures(emptyList())
        )
    )

    style.addLayer(
        org.maplibre.android.style.layers.FillLayer("radar-zones-fill", "radar-zones-source").apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.fillColor(Color.parseColor("#2196F3")),
                org.maplibre.android.style.layers.PropertyFactory.fillOpacity(0.2f)
            )
        }
    )

    style.addLayer(
        org.maplibre.android.style.layers.LineLayer("radar-zones-outline", "radar-zones-source").apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#1976D2")),
                org.maplibre.android.style.layers.PropertyFactory.lineWidth(2f)
            )
        }
    )

    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            radarSourceId,
            FeatureCollection.fromFeatures(emptyList())
        )
    )

    style.addLayer(
        org.maplibre.android.style.layers.SymbolLayer(radarLayerId, radarSourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage(
                    org.maplibre.android.style.expressions.Expression.get("iconId")
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconSize(1.0f),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true)
            )
        }
    )

    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            routeAltSourceId,
            FeatureCollection.fromFeatures(emptyList())
        )
    )
    style.addLayer(
        org.maplibre.android.style.layers.LineLayer("route-alt-border-layer", routeAltSourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#0D47A1")),
                org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(6, 5f),
                        org.maplibre.android.style.expressions.Expression.stop(14, 8f),
                        org.maplibre.android.style.expressions.Expression.stop(18, 13f)
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineCap(
                    org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                    org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                )
            )
        }
    )
    style.addLayer(
        org.maplibre.android.style.layers.LineLayer(routeAltLayerId, routeAltSourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#D6E4E8")),
                org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(6, 3f),
                        org.maplibre.android.style.expressions.Expression.stop(14, 5f),
                        org.maplibre.android.style.expressions.Expression.stop(18, 9f)
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineCap(
                    org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                    org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                )
            )
        }
    )

    style.addLayer(
        org.maplibre.android.style.layers.LineLayer(routeAltHitareaLayerId, routeAltSourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#000000")),
                org.maplibre.android.style.layers.PropertyFactory.lineOpacity(0.001f),
                org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(6, 24f),
                        org.maplibre.android.style.expressions.Expression.stop(14, 32f),
                        org.maplibre.android.style.expressions.Expression.stop(18, 44f)
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineCap(
                    org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                    org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                )
            )
        }
    )

    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            "route-source",
            FeatureCollection.fromFeatures(emptyList())
        )
    )

    style.addLayer(
        org.maplibre.android.style.layers.LineLayer("route-border-layer", "route-source").apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#081B33")),
                org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(6, 6f),
                        org.maplibre.android.style.expressions.Expression.stop(14, 10f),
                        org.maplibre.android.style.expressions.Expression.stop(18, 16f)
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineCap(
                    org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                    org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                )
            )
        }
    )

    style.addLayer(
        org.maplibre.android.style.layers.LineLayer("route-layer", "route-source").apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#0D47A1")),
                org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(6, 4f),
                        org.maplibre.android.style.expressions.Expression.stop(14, 7f),
                        org.maplibre.android.style.expressions.Expression.stop(18, 12f)
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineCap(
                    org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                    org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                )
            )
        }
    )

    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            routeLabelSourceId,
            FeatureCollection.fromFeatures(emptyList())
        )
    )

    style.addLayer(
        org.maplibre.android.style.layers.SymbolLayer(routeLabelLayerId, routeLabelSourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage(
                    org.maplibre.android.style.expressions.Expression.get("iconId")
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconSize(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(6, 0.5f),
                        org.maplibre.android.style.expressions.Expression.stop(10, 0.65f),
                        org.maplibre.android.style.expressions.Expression.stop(14, 0.8f),
                        org.maplibre.android.style.expressions.Expression.stop(18, 1.0f)
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconOffset(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(
                            6,
                            org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -10f))
                        ),
                        org.maplibre.android.style.expressions.Expression.stop(
                            10,
                            org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -13f))
                        ),
                        org.maplibre.android.style.expressions.Expression.stop(
                            14,
                            org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -16f))
                        ),
                        org.maplibre.android.style.expressions.Expression.stop(
                            18,
                            org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -18f))
                        )
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true),
                org.maplibre.android.style.layers.PropertyFactory.iconAnchor(
                    org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
                )
            )
        }
    )

    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            routeAltLabelSourceId,
            FeatureCollection.fromFeatures(emptyList())
        )
    )

    style.addLayer(
        org.maplibre.android.style.layers.SymbolLayer(routeAltLabelLayerId, routeAltLabelSourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage(
                    org.maplibre.android.style.expressions.Expression.get("iconId")
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconSize(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(6, 0.5f),
                        org.maplibre.android.style.expressions.Expression.stop(10, 0.65f),
                        org.maplibre.android.style.expressions.Expression.stop(14, 0.8f),
                        org.maplibre.android.style.expressions.Expression.stop(18, 1.0f)
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconOffset(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.zoom(),
                        org.maplibre.android.style.expressions.Expression.stop(
                            6,
                            org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -10f))
                        ),
                        org.maplibre.android.style.expressions.Expression.stop(
                            10,
                            org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -13f))
                        ),
                        org.maplibre.android.style.expressions.Expression.stop(
                            14,
                            org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -16f))
                        ),
                        org.maplibre.android.style.expressions.Expression.stop(
                            18,
                            org.maplibre.android.style.expressions.Expression.literal(arrayOf(0f, -18f))
                        )
                    )
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true),
                org.maplibre.android.style.layers.PropertyFactory.iconAnchor(
                    org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
                )
            )
        }
    )

    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            destinationSourceId,
            FeatureCollection.fromFeatures(emptyList())
        )
    )

    style.addLayer(
        org.maplibre.android.style.layers.SymbolLayer(destinationLayerId, destinationSourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage(destinationIconId),
                org.maplibre.android.style.layers.PropertyFactory.iconSize(1.0f),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true)
            )
        }
    )

    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            userSourceId,
            FeatureCollection.fromFeatures(emptyList())
        )
    )

    style.addLayer(
        org.maplibre.android.style.layers.SymbolLayer(userLayerId, userSourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage(userIconId),
                org.maplibre.android.style.layers.PropertyFactory.iconSize(
                    org.maplibre.android.style.expressions.Expression.get("iconScale")
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconRotate(
                    org.maplibre.android.style.expressions.Expression.get("rotation")
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment(
                    org.maplibre.android.style.layers.Property.ICON_ROTATION_ALIGNMENT_MAP
                ),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true)
            )
        }
    )
}

private fun createDestinationBitmap(context: android.content.Context): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.BLACK
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 3.5f, paint)

    paint.color = android.graphics.Color.BLACK
    canvas.drawCircle(size / 2f, size / 2f, size / 7f, paint)

    return bitmap
}