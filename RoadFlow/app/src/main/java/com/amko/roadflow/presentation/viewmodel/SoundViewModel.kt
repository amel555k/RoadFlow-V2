package com.amko.roadflow.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TtsLanguage {
    BOSNIAN, ENGLISH
}

class SoundViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("sound_settings", Context.MODE_PRIVATE)

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled

    private val _ttsEnabled = MutableStateFlow(prefs.getBoolean("tts_enabled", true))
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled

    private val _ttsLanguage = MutableStateFlow(loadTtsLanguage())
    val ttsLanguage: StateFlow<TtsLanguage> = _ttsLanguage

    private val _alertRadius = MutableStateFlow(prefs.getInt("alert_radius", 200))
    val alertRadius: StateFlow<Int> = _alertRadius

    fun setAlertRadius(radius: Int) {
        _alertRadius.value = radius
        prefs.edit().putInt("alert_radius", radius).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }

    fun setTtsEnabled(enabled: Boolean) {
        _ttsEnabled.value = enabled
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
    }

    fun setTtsLanguage(language: TtsLanguage) {
        _ttsLanguage.value = language
        prefs.edit().putString("tts_language", language.name).apply()
    }

    private fun loadTtsLanguage(): TtsLanguage {
        val name = prefs.getString("tts_language", TtsLanguage.BOSNIAN.name)
        return try {
            TtsLanguage.valueOf(name ?: TtsLanguage.BOSNIAN.name)
        } catch (e: Exception) {
            TtsLanguage.BOSNIAN
        }
    }
}