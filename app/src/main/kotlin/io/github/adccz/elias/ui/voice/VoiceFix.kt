package io.github.adccz.elias.ui.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import io.github.adccz.elias.domain.voice.TtsEngine
import io.github.adccz.elias.domain.voice.VoiceStatus

/**
 * Opens the screen that fixes [status]: the engine's voice download when a voice is missing, otherwise the system
 * text-to-speech settings, where another engine can be chosen. Phones differ, so it falls back to the
 * accessibility settings, which lead to text-to-speech too (on Samsung: Usnadnění).
 */
fun Context.openVoiceFix(status: VoiceStatus) {
    val candidates = buildList {
        when (status) {
            is VoiceStatus.NotDownloaded -> add(installVoiceData(status.engine))
            is VoiceStatus.OnlineOnly -> add(installVoiceData(status.engine))
            else -> Unit
        }
        add(Intent(TTS_SETTINGS))
        add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
    for (intent in candidates) {
        try {
            startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // This phone has no such screen. Try the next one.
        }
    }
}

/** The engine's own screen for downloading voice data. */
private fun installVoiceData(engine: TtsEngine) =
    Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(engine.packageName)

/** Not a public constant, but Android's settings and Samsung's open text-to-speech settings with it. */
private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
