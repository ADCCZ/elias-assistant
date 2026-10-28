package io.github.adccz.elias.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.overview.TodayOverviewLoader
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the home screen shows. The screen only renders this, it holds no logic. */
data class HomeUiState(val today: LocalDate, val speech: SpeechUiState = SpeechUiState.Idle)

/**
 * State holder for the home screen.
 *
 * Hilt creates it and passes the app [Clock] and the other dependencies into the constructor (constructor injection).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    clock: Clock,
    private val loader: TodayOverviewLoader,
    private val briefings: BriefingService,
    private val speech: SpeechController,
) : ViewModel() {
    private val today = LocalDate.now(clock)

    val uiState: StateFlow<HomeUiState> = speech.state
        .map { HomeUiState(today, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(today))

    private var playJob: Job? = null

    /** Loads today's data, writes the briefing (AI first, the template as fallback) and speaks it. */
    fun play(kind: BriefingKind) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            speech.speak(kind) {
                val overview = loader.load()
                when (kind) {
                    BriefingKind.MORNING -> briefings.morning(overview).briefing
                    BriefingKind.EVENING -> briefings.evening(overview).briefing
                }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        speech.stop()
    }
}
