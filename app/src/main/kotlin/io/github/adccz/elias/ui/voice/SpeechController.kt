package io.github.adccz.elias.ui.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackTrigger
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.SpeechState
import io.github.adccz.elias.domain.voice.VoicePlayer
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a screen shows about Elias speaking. */
sealed interface SpeechUiState {
    data object Idle : SpeechUiState

    /** Writing the briefing and turning it into speech, which takes a few seconds. */
    data class Preparing(val kind: BriefingKind) : SpeechUiState

    data class Speaking(val kind: BriefingKind, val isPaused: Boolean) : SpeechUiState

    data class Failed(val kind: BriefingKind, val problem: SpeakResult.Problem) : SpeechUiState
}

/**
 * Lets a screen speak briefings and tells it what is happening. The work itself runs in the playback service,
 * so it goes on when the screen closes, and every screen sees the same state.
 */
class SpeechController @Inject constructor(private val player: VoicePlayer) {
    val state: Flow<SpeechUiState> = player.state.map { state ->
        when (state) {
            SpeechState.Idle -> SpeechUiState.Idle
            is SpeechState.Preparing -> SpeechUiState.Preparing(state.kind)
            is SpeechState.Speaking -> SpeechUiState.Speaking(state.kind, state.isPaused)
            is SpeechState.Failed -> SpeechUiState.Failed(state.kind, state.problem)
        }
    }

    /** Writes today's briefing of [kind] from fresh data and speaks it. */
    fun speakToday(kind: BriefingKind) = player.speak(BriefingRequest.FromToday(kind, PlaybackTrigger.USER))

    /** Speaks exactly [briefing], as it is shown on the screen. */
    fun speak(briefing: BriefingText) = player.speak(BriefingRequest.Written(briefing))

    fun stop() = player.stop()
}
