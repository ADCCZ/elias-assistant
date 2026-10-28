package io.github.adccz.elias.testing

import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackStream
import io.github.adccz.elias.domain.voice.PlaylistPlayer
import io.github.adccz.elias.domain.voice.SpeechClip
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.SpeechState
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

// Fakes for the voice interfaces, shared by the runner and the ViewModel tests.

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
class FakePlaylistPlayer(var accepts: Boolean = true) : PlaylistPlayer {
    val played = mutableListOf<Pair<VoicePlaylist, PlaybackStream>>()

    override suspend fun play(playlist: VoicePlaylist, stream: PlaybackStream): Boolean {
        played += playlist to stream
        return accepts
    }
}

/** Stands in for the playback service: records the requests, and a test sets [state] as the service would. */
class FakeVoicePlayer : VoicePlayer {
    override val state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val requests = mutableListOf<BriefingRequest>()
    var stops = 0

    override fun speak(request: BriefingRequest) {
        requests += request
        state.value = SpeechState.Preparing(request.kind)
    }

    override fun stop() {
        stops++
        state.value = SpeechState.Idle
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
