package com.amko.roadflow.presentation.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.amko.roadflow.data.local.RadarParser
import com.amko.roadflow.data.local.FirebaseService
import com.amko.roadflow.data.local.NoInternetWithCacheException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WidgetRefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        prefs.edit().apply {
            putBoolean("widget_loading", true)
            putBoolean("no_internet_error", false)
            apply()
        }

        FavoriteCitiesWidget().updateAll(context)

        try {
            withContext(Dispatchers.IO) {
                val parser = RadarParser(context, FirebaseService())
                val radars = mutableListOf<com.amko.roadflow.domain.model.RadarData>()

                parser.parseAllLocationsAsFlow().collect { newRadars ->
                    radars.clear()
                    radars.addAll(newRadars)
                }

                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val timestamp = LocalDateTime.now().format(formatter)

                prefs.edit().apply {
                    putBoolean("widget_loading", false)
                    putString("widget_last_update", timestamp)
                    putBoolean("no_internet_error", false)
                    apply()
                }
            }
        } catch (e: NoInternetWithCacheException) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val timestamp = LocalDateTime.now().format(formatter)

            prefs.edit().apply {
                putBoolean("widget_loading", false)
                putString("widget_last_update", "$timestamp (cache)")
                putBoolean("no_internet_error", true)
                apply()
            }
        } catch (e: Exception) {
            prefs.edit().apply {
                putBoolean("widget_loading", false)
                putBoolean("no_internet_error", true)
                apply()
            }
        }

        FavoriteCitiesWidget().updateAll(context)
    }
}