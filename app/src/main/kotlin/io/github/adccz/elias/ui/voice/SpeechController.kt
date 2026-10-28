package io.github.adccz.elias.ui.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.voice.BriefingSpeaker
import io.github.adccz.elias.domain.voice.PlaybackState
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.VoicePlayer
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** What a screen shows about Elias speaking. */
sealed interface SpeechUiState {
    data object Idle : SpeechUiState

    /** Writing the briefing and turning it into speech, which takes a few seconds. */
    data class Preparing(val kind: BriefingKind) : SpeechUiState

    data class Speaking(val kind: BriefingKind, val isPaused: Boolean) : SpeechUiState

    data class Failed(val kind: BriefingKind, val problem: SpeakResult.Problem) : SpeechUiState
}

/**
 * Lets a screen speak briefings and tells it what is happening, by combining the preparation with the player's
 * state. Each ViewModel gets its own instance; the player behind it is shared, so every screen sees the same
 * playback. Used on the main thread only.
 */
class SpeechController @Inject constructor(private val speaker: BriefingSpeaker, private val player: VoicePlayer) {
    /** Preparing or Failed while that matters, otherwise null and the player's state is shown. */
    private val ownState = MutableStateFlow<SpeechUiState?>(null)

    /** Counts requests, so a request that was replaced or stopped cannot change the state any more. */
    private var request = 0

    val state: Flow<SpeechUiState> = combine(ownState, player.state) { own, playback ->
        own ?: when (playback) {
            PlaybackState.Idle -> SpeechUiState.Idle
            is PlaybackState.Speaking -> SpeechUiState.Speaking(playback.kind, playback.isPaused)
        }
    }

    /** Writes the briefing with [write] and speaks it. Safe to cancel, for example by a newer request. */
    suspend fun speak(kind: BriefingKind, write: suspend () -> BriefingText) {
        val thisRequest = ++request
        ownState.value = SpeechUiState.Preparing(kind)
        try {
            val result = speaker.speak(write())
            if (request == thisRequest) {
                ownState.value = (result as? SpeakResult.Problem)?.let { SpeechUiState.Failed(kind, it) }
            }
        } finally {
            // Cancelled while preparing: nothing is happening any more.
            if (request == thisRequest && ownState.value is SpeechUiState.Preparing) ownState.value = null
        }
    }

    fun stop() {
        request++
        ownState.value = null
        player.stop()
    }
}
