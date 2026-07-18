package com.amko.roadflow.presentation.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class FavoriteCitiesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: FavoriteCitiesWidget = FavoriteCitiesWidget()
}