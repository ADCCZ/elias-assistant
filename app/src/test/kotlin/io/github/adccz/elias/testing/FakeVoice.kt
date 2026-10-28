package io.github.adccz.elias.testing

import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.voice.PlaybackState
import io.github.adccz.elias.domain.voice.PlaybackStream
import io.github.adccz.elias.domain.voice.SpeechClip
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.SpeechSynthesizer
import io.github.adccz.elias.domain.voice.SynthesisResult
import io.github.adccz.elias.domain.voice.TtsEngine
import io.github.adccz.elias.domain.voice.VoiceChecker
import io.github.adccz.elias.domain.voice.VoicePlayer
import io.github.adccz.elias.domain.voice.VoicePlaylist
import io.github.adccz.elias.domain.voice.VoiceSettingsRepository
import io.github.adccz.elias.domain.voice.VoiceStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

// Fakes for the voice interfaces, shared by the speaker and the ViewModel tests.

val testEngine = TtsEngine("com.example.tts", "Převod textu na řeč")

/** Turns each section into a pretend clip, or returns [result] when it is set. */
class FakeSpeechSynthesizer : SpeechSynthesizer {
    var result: SynthesisResult? = null

    /** When set, synthesis waits for it, so a test can look at the "preparing" state. */
    var gate: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<Pair<List<BriefingSection>, SpeechRate>>()

    override suspend fun synthesize(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult {
        requests += sections to rate
        gate?.await()
        val clips = sections.mapIndexed { index, section -> SpeechClip(section.kind, "voice/clip-$index.wav") }
        return result ?: SynthesisResult.Success(clips)
    }
}

/** Records what it was asked to play. [accepts] decides whether playback starts. */
class FakeVoicePlayer(var accepts: Boolean = true) : VoicePlayer {
    override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val played = mutableListOf<Pair<VoicePlaylist, PlaybackStream>>()
    var stops = 0

    override suspend fun play(playlist: VoicePlaylist, stream: PlaybackStream): Boolean {
        played += playlist to stream
        if (accepts) state.value = PlaybackState.Speaking(playlist.kind, isPaused = false)
        return accepts
    }

    override fun stop() {
        stops++
        state.value = PlaybackState.Idle
    }
}

class FakeVoiceSettings(rate: SpeechRate = SpeechRate.NORMAL) : VoiceSettingsRepository {
    override val speechRate = MutableStateFlow(rate)

    override suspend fun setSpeechRate(rate: SpeechRate) {
        speechRate.value = rate
    }
}

class FakeVoiceChecker(var status: VoiceStatus = VoiceStatus.Ready(testEngine, "cs-local")) : VoiceChecker {
    var checks = 0

    override suspend fun check(): VoiceStatus {
        checks++
        return status
    }
}
