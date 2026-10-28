package io.github.adccz.elias.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingResult
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.overview.TodayOverviewLoader
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.VoiceChecker
import io.github.adccz.elias.domain.voice.VoiceSettingsRepository
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewUiState(
    val selected: BriefingKind = BriefingKind.MORNING,
    val useAi: Boolean = true,
    val isLoading: Boolean = true,
    val morning: BriefingResult? = null,
    val evening: BriefingResult? = null,
    val speech: SpeechUiState = SpeechUiState.Idle,
    val speechRate: SpeechRate = SpeechRate.NORMAL,
    /** The Czech voice check. Null until the first check is done. */
    val voice: VoiceStatus? = null,
) {
    /** The briefing for the selected tab, or null before the first one is ready. */
    val current: BriefingResult? get() = if (selected == BriefingKind.MORNING) morning else evening
}

/**
 * Builds both briefings from today's data, so they can be read and compared, and speaks the one on the screen.
 * The speech rate is set here too, and the screen shows whether the phone can speak Czech offline.
 */
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val loader: TodayOverviewLoader,
    private val briefings: BriefingService,
    private val speech: SpeechController,
    private val voiceChecker: VoiceChecker,
    private val voiceSettings: VoiceSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null
    private var voiceJob: Job? = null

    init {
        generate()
        checkVoice()
        viewModelScope.launch {
            speech.state.collect { state -> _uiState.update { it.copy(speech = state) } }
        }
        viewModelScope.launch {
            voiceSettings.speechRate.collect { rate -> _uiState.update { it.copy(speechRate = rate) } }
        }
    }

    /** Loads today's data and writes both briefings. A running generation is replaced. */
    fun generate() {
        generateJob?.cancel()
        val useAi = _uiState.value.useAi
        _uiState.update { it.copy(isLoading = true) }
        generateJob = viewModelScope.launch {
            val overview = loader.load()
            val morning = async { briefings.morning(overview, useAi) }
            val evening = async { briefings.evening(overview, useAi) }
            val results = morning.await() to evening.await()
            _uiState.update { it.copy(isLoading = false, morning = results.first, evening = results.second) }
        }
    }

    fun select(kind: BriefingKind) = _uiState.update { it.copy(selected = kind) }

    fun setUseAi(enabled: Boolean) {
        _uiState.update { it.copy(useAi = enabled) }
        generate()
    }

    /** Speaks the briefing on the screen, exactly as it is written there. */
    fun play() {
        val briefing = _uiState.value.current?.briefing ?: return
        speech.speak(briefing)
    }

    fun stop() = speech.stop()

    /** While the slider moves, only the screen changes. */
    fun onSpeechRateChange(value: Float) = _uiState.update { it.copy(speechRate = SpeechRate.of(value)) }

    /** When the slider is let go, the rate is saved. The next playback uses it. */
    fun onSpeechRateChangeFinished() {
        val rate = _uiState.value.speechRate
        viewModelScope.launch { voiceSettings.setSpeechRate(rate) }
    }

    /** Checks the Czech voice. The last result stays on the screen until the new one is known. */
    fun checkVoice() {
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            val status = voiceChecker.check()
            _uiState.update { it.copy(voice = status) }
        }
    }

    /** Back from the voice settings: check again if the voice was not ready. */
    fun onScreenResumed() {
        val voice = _uiState.value.voice
        if (voice != null && voice !is VoiceStatus.Ready) checkVoice()
    }
}
