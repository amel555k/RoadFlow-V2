package com.amko.roadflow.data.local

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.location.Location
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.amko.roadflow.R
import com.amko.roadflow.domain.model.RadarData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask

class RadarAlertService(private val context: Context) {

    private var activeRadars: List<RadarData> = emptyList()
    private var isInsideZone = false
    private var currentSpeedLimit = 0
    private var lastSpeedKmh = 0.0
    private var alertTimer: Timer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var useFallbackEnglish = false
    private var requestedLanguageIsEnglish = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val soundPrefs = context.getSharedPreferences("sound_settings", Context.MODE_PRIVATE)

    private fun isVibrationEnabled(): Boolean = soundPrefs.getBoolean("vibration_enabled", true)
    private fun isTtsEnabled(): Boolean = soundPrefs.getBoolean("tts_enabled", true)
    private fun isEnglishSelected(): Boolean = soundPrefs.getString("tts_language", "BOSNIAN") == "ENGLISH"
    private fun getAlertRadius(): Double = soundPrefs.getInt("alert_radius", 200).toDouble()

    private val _speedLimit = MutableStateFlow(0)
    val speedLimit: StateFlow<Int> = _speedLimit.asStateFlow()

    private val _isInZone = MutableStateFlow(false)
    val isInZone: StateFlow<Boolean> = _isInZone.asStateFlow()

    init {
        prepareAudio()
        prepareTts()
    }

    private fun prepareTts() {
        val googleTtsEngine = "com.google.android.tts"
        textToSpeech = TextToSpeech(context.applicationContext, { status ->
            if (status == TextToSpeech.SUCCESS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    textToSpeech?.setAudioAttributes(audioAttributes)
                }

                applyTtsLanguage(isEnglishSelected())
            }
        }, googleTtsEngine)
    }

    private fun applyTtsLanguage(englishRequested: Boolean) {
        requestedLanguageIsEnglish = englishRequested

        if (englishRequested) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                useFallbackEnglish = true
                return
            }
        }

        val localeBalkan = Locale("hr", "HR")
        val result = textToSpeech?.setLanguage(localeBalkan)

        if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
            isTtsReady = true
            useFallbackEnglish = false
        } else {
            val fallbackResult = textToSpeech?.setLanguage(Locale.US)
            if (fallbackResult != TextToSpeech.LANG_MISSING_DATA && fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                useFallbackEnglish = true
            } else {
                isTtsReady = false
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
    }

    private fun speakZoneEntry(speedLimit: Int, isStacionaran: Boolean, speedKmh: Double) {
        if (!isTtsEnabled() || !isTtsReady) {
            startAlertLoop(speedKmh)
            return
        }

        if (requestedLanguageIsEnglish != isEnglishSelected()) {
            applyTtsLanguage(isEnglishSelected())
        }

        requestAudioFocus()

        requestAudioFocus()

        val message = if (useFallbackEnglish) {
            val cameraType = if (isStacionaran) "Stationary camera" else "Mobile camera"
            if (speedLimit > 0) {
                "$cameraType nearby, speed limit $speedLimit kilometers per hour."
            } else {
                "$cameraType nearby, slow down."
            }
        } else {
            val cameraType = if (isStacionaran) "Stacionarna kamera" else "Mobilna kamera"
            if (speedLimit > 0) {
                "$cameraType u blizini, ograničenje $speedLimit kilometara na sat."
            } else {
                "$cameraType u blizini, usporite."
            }
        }

        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                abandonAudioFocus()
                if (isInsideZone) startAlertLoop(speedKmh)
            }
            override fun onError(utteranceId: String?) {
                abandonAudioFocus()
                if (isInsideZone) startAlertLoop(speedKmh)
            }
        })
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "radar_zone_entry")
    }

    private fun prepareAudio() {
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.beep)
            mediaPlayer?.isLooping = false
        } catch (e: Exception) {
            android.util.Log.e("RadarAlertService", "Greška pri pripremi audio: ${e.message}")
        }
    }

    fun setActiveRadars(radars: List<RadarData>) {
        activeRadars = radars
    }

    fun checkProximity(location: Location) {
        if (activeRadars.isEmpty()) {
            if (isInsideZone) exitZone()
            return
        }

        val nearestRadar = activeRadars
            .filter { it.latitude != null && it.longitude != null }
            .map { radar ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude, location.longitude,
                    radar.latitude!!, radar.longitude!!,
                    results
                )
                Pair(radar, results[0].toDouble())
            }
            .filter { (_, distance) -> distance <= getAlertRadius() }
            .minByOrNull { (_, distance) -> distance }

        if (nearestRadar != null) {
            val (radar, _) = nearestRadar
            val speedKmh = (location.speed * 3.6)
            val speedLimit = radar.speedLimit ?: radar.coordinate?.speedLimit ?: 0
            val isStacionaran = radar.coordinate?.stacionaran == true

            if (!isInsideZone) {
                enterZone(speedLimit, speedKmh, isStacionaran)
            } else {
                val newInterval = getAlertInterval(speedKmh, currentSpeedLimit)
                val currentInterval = getAlertInterval(lastSpeedKmh, currentSpeedLimit)
                if (newInterval != currentInterval) {
                    lastSpeedKmh = speedKmh
                    startAlertLoop(speedKmh)
                }
            }
        } else if (isInsideZone) {
            exitZone()
        }
    }

    private fun getAlertInterval(speedKmh: Double, speedLimit: Int): Int {
        if (speedLimit <= 0) return 3
        val over = speedKmh - speedLimit
        return when {
            over >= 10 -> 1
            over >= 5  -> 2
            else       -> 3
        }
    }

    private fun enterZone(speedLimit: Int, speedKmh: Double, isStacionaran: Boolean) {
        isInsideZone = true
        currentSpeedLimit = speedLimit
        lastSpeedKmh = speedKmh
        _isInZone.value = true
        _speedLimit.value = speedLimit
        speakZoneEntry(speedLimit, isStacionaran, speedKmh)
    }

    private fun exitZone() {
        isInsideZone = false
        currentSpeedLimit = 0
        lastSpeedKmh = 0.0
        _isInZone.value = false
        _speedLimit.value = 0
        textToSpeech?.stop()
        stopAlertLoop()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
        }
        prepareAudio()
        abandonAudioFocus()
    }
    private fun startAlertLoop(speedKmh: Double) {
        stopAlertLoop()
        val intervalSeconds = getAlertInterval(speedKmh, currentSpeedLimit)
        playBeep()
        alertTimer = Timer()
        alertTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                playBeep()
            }
        }, intervalSeconds * 1000L, intervalSeconds * 1000L)
    }

    private fun stopAlertLoop() {
        alertTimer?.cancel()
        alertTimer = null
    }

    private fun playBeep() {
        try {
            if (isVibrationEnabled()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator.vibrate(
                        VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(500)
                    }
                }
            }
            requestAudioFocus()
            mediaPlayer?.let {
                if (it.isPlaying) it.seekTo(0)
                else it.start()
            }
        } catch (e: Exception) {
            android.util.Log.e("RadarAlertService", "Greška pri beep-u: ${e.message}")
        }
    }

    fun stopAlerts() {
        if (isInsideZone) exitZone()
    }

    fun dispose() {
        stopAlertLoop()
        mediaPlayer?.release()
        mediaPlayer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}