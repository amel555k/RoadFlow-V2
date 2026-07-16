package com.amko.roadflow.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.amko.roadflow.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadTheme())
    val themeMode: StateFlow<AppTheme> = _themeMode

    fun setThemeMode(theme: AppTheme) {
        _themeMode.value = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun toggleTheme() {
        val newTheme = if (_themeMode.value == AppTheme.LIGHT) AppTheme.DARK else AppTheme.LIGHT
        setThemeMode(newTheme)
    }

    private fun loadTheme(): AppTheme {
        val themeName = prefs.getString("selected_theme", AppTheme.LIGHT.name)
        return try {
            AppTheme.valueOf(themeName ?: AppTheme.LIGHT.name)
        } catch (e: Exception) {
            AppTheme.LIGHT
        }
    }
}