package io.github.adccz.elias.data.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.voice.SpeechClip
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.SpeechSynthesizer
import io.github.adccz.elias.domain.voice.SynthesisResult
import io.github.adccz.elias.domain.voice.TtsEngine
import io.github.adccz.elias.domain.voice.TtsVoice
import io.github.adccz.elias.domain.voice.VoiceChecker
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.domain.voice.VoiceStatusEvaluator
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
 * Speech goes into files (`synthesizeToFile`), not to the speaker: Elias plays the files itself in a playback
 * service, which handles pause, audio focus and the notification (see `PlaybackService`).
 */
@Singleton
class AndroidTextToSpeech @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val files: VoiceFiles,
) : VoiceChecker,
    SpeechSynthesizer {
    /** One engine session at a time: the engine has one queue and one set of settings (voice, speed). */
    private val mutex = Mutex()

    override suspend fun check(): VoiceStatus = mutex.withLock {
        withEngine { tts -> status(tts) } ?: VoiceStatus.EngineUnavailable
    }

    override suspend fun synthesize(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult =
        mutex.withLock {
            withEngine { tts ->
                when (val status = status(tts)) {
                    is VoiceStatus.Ready -> synthesizeClips(tts, status.voiceName, sections, rate)
                    else -> SynthesisResult.VoiceNotReady(status)
                }
            } ?: SynthesisResult.VoiceNotReady(VoiceStatus.EngineUnavailable)
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

    /** Speaks each section into its own file, one after another. Leaves no clips behind when it fails. */
    private suspend fun synthesizeClips(
        tts: TextToSpeech,
        voiceName: String,
        sections: List<BriefingSection>,
        rate: SpeechRate,
    ): SynthesisResult {
        val voice = tts.voices.orEmpty().firstOrNull { it.name == voiceName }
            ?: return SynthesisResult.Failure("Voice $voiceName is gone")
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS) return SynthesisResult.Failure("setVoice($voiceName) failed")
        tts.setSpeechRate(rate.multiplier)
        val progress = ClipProgress()
        tts.setOnUtteranceProgressListener(progress)

        files.deleteClips()
        val clips = mutableListOf<SpeechClip>()
        for ((index, section) in sections.withIndex()) {
            val file = files.clip(index)
            val failure = synthesizeClip(tts, progress, section, file, utteranceId = "clip-$index")
            if (failure != null) {
                files.deleteClips()
                return SynthesisResult.Failure(failure)
            }
            clips += SpeechClip(section.kind, file.path)
        }
        return SynthesisResult.Success(clips)
    }

    /** Speaks one section into [file]. Returns why it failed, or null when the clip is ready. */
    private suspend fun synthesizeClip(
        tts: TextToSpeech,
        progress: ClipProgress,
        section: BriefingSection,
        file: File,
        utteranceId: String,
    ): String? {
        val done = progress.expect(utteranceId)
        if (tts.synthesizeToFile(section.text, Bundle(), file, utteranceId) != TextToSpeech.SUCCESS) {
            return "The engine refused ${section.kind}"
        }
        if (withTimeoutOrNull(CLIP_TIMEOUT_MILLIS) { done.await() } != true) return "No audio for ${section.kind}"
        if (file.length() <= SilenceWav.HEADER_BYTES) return "Empty audio for ${section.kind}"
        return null
    }

    /** Completes a waiting clip when the engine reports it done (true) or failed (false). Called on engine threads. */
    private class ClipProgress : UtteranceProgressListener() {
        private val waiting = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

        fun expect(utteranceId: String): CompletableDeferred<Boolean> =
            CompletableDeferred<Boolean>().also { waiting[utteranceId] = it }

        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finish(utteranceId, success = true)

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, success = false)

        @Deprecated("Replaced by onError(String, Int) in API 21, but engines may still call it.")
        override fun onError(utteranceId: String?) = finish(utteranceId, success = false)

        private fun finish(utteranceId: String?, success: Boolean) {
            if (utteranceId != null) waiting.remove(utteranceId)?.complete(success)
        }
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

        /** One section is a few sentences. Even a slow phone writes it in a few seconds. */
        const val CLIP_TIMEOUT_MILLIS = 30_000L
    }
}
