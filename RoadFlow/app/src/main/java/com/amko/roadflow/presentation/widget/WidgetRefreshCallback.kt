package com.amko.roadflow.presentation.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.RadarParser
import com.amko.roadflow.domain.model.RadarData
import java.io.File
import java.time.LocalDate

class WidgetRefreshCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val filePath = File(context.filesDir, "lista.txt")
        val todayDate = LocalDate.now()

        val fileIsValid = filePath.exists() &&
                LocalDate.ofEpochDay(filePath.lastModified() / 86400000L) == todayDate

        if (!fileIsValid) {
            val parser = RadarParser(context, FirebaseService())
            try {
                var lastEmit = emptyList<RadarData>()
                parser.parseAllLocationsAsFlow().collect { lastEmit = it }
            } catch (e: Exception) {

            }
        }

        FavoriteCitiesWidget().updateAll(context)
    }
}