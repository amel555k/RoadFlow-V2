package com.amko.roadflow.presentation.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class FavoriteCitiesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: FavoriteCitiesWidget = FavoriteCitiesWidget()
}