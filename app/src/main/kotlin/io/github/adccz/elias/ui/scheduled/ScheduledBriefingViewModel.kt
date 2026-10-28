package io.github.adccz.elias.ui.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/** What the evening briefing screen shows. [isOver] becomes true when the briefing has ended, so the screen closes. */
data class ScheduledBriefingUiState(
    val speech: SpeechUiState = SpeechUiState.Idle,
    val wasActive: Boolean = false,
    val isOver: Boolean = false,
)

/** The screen that lights up for the scheduled evening briefing. It starts the briefing and follows it. */
@HiltViewModel
class ScheduledBriefingViewModel @Inject constructor(private val speech: SpeechController) : ViewModel() {
    val uiState: StateFlow<ScheduledBriefingUiState> = speech.state
        .scan(ScheduledBriefingUiState()) { previous, state ->
            val active = state is SpeechUiState.Preparing || state is SpeechUiState.Speaking
            val wasActive = previous.wasActive || active
            ScheduledBriefingUiState(
                speech = state,
                wasActive = wasActive,
                isOver =
                    wasActive && state == SpeechUiState.Idle,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScheduledBriefingUiState())

    /**
     * Starts the evening briefing while this screen is visible: Android 17 always lets a visible app play sound.
     * A briefing that is already running (the user opened the screen again) is left alone.
     */
    fun start() {
        if (!speech.isBusy) speech.speakScheduledEvening()
    }

    fun stop() = speech.stop()
}
