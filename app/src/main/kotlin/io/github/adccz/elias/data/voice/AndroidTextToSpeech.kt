package io.github.adccz.elias.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.domain.voice.TtsEngine
import io.github.adccz.elias.domain.voice.TtsVoice
import io.github.adccz.elias.domain.voice.VoiceChecker
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.domain.voice.VoiceStatusEvaluator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Talks to the phone's text-to-speech engine (Google, Samsung...). The Android part stays thin:
 * it only copies what the engine reports, the decisions live in [VoiceStatusEvaluator].
 *
 * Every call starts the engine, does its work and shuts the engine down, so no connection stays open.
 */
@Singleton
class AndroidTextToSpeech @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : VoiceChecker {
    /** One engine session at a time: the engine has one queue and one set of settings (voice, speed). */
    private val mutex = Mutex()

    override suspend fun check(): VoiceStatus = mutex.withLock {
        withEngine { tts -> status(tts) } ?: VoiceStatus.EngineUnavailable
    }

    /** Starts an engine, runs [block] with it off the main thread and always shuts it down. Null if it did not start. */
    private suspend fun <T> withEngine(block: suspend (TextToSpeech) -> T): T? {
        val started = CompletableDeferred<Boolean>()
        // The engine reports its start on the main thread, so it is created there too.
        val tts = withContext(Dispatchers.Main) {
            TextToSpeech(context) { status -> started.complete(status == TextToSpeech.SUCCESS) }
        }
        try {
            val isStarted = withTimeoutOrNull(START_TIMEOUT_MILLIS) { started.await() } ?: false
            // Calls to the engine go to another app, so they must not block the main thread.
            return if (isStarted) withContext(io) { block(tts) } else null
        } finally {
            tts.shutdown()
        }
    }

    private fun status(tts: TextToSpeech): VoiceStatus {
        val packageName = tts.defaultEngine ?: return VoiceStatus.EngineUnavailable
        val label = tts.engines.firstOrNull { it.name == packageName }?.label ?: packageName
        val defaultVoice = tts.defaultVoice?.name
        val voices = tts.voices.orEmpty().map { it.toTtsVoice(isDefault = it.name == defaultVoice) }
        return VoiceStatusEvaluator.evaluate(TtsEngine(packageName, label), voices)
    }

    private fun Voice.toTtsVoice(isDefault: Boolean) = TtsVoice(
        name = name,
        language = locale.language,
        needsNetwork = isNetworkConnectionRequired,
        notInstalled = features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true,
        quality = quality,
        isDefault = isDefault,
    )

    private companion object {
        /** A cold engine (first use after boot) can take a few seconds to start. */
        const val START_TIMEOUT_MILLIS = 10_000L
    }
}
