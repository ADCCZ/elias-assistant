package io.github.adccz.elias.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingResult
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.overview.TodayOverviewLoader
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
) {
    /** The briefing for the selected tab, or null before the first one is ready. */
    val current: BriefingResult? get() = if (selected == BriefingKind.MORNING) morning else evening
}

/** Builds both briefings from today's data, so they can be read and compared before Elias speaks them (M3). */
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val loader: TodayOverviewLoader,
    private val briefings: BriefingService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        generate()
    }

    /** Loads today's data and writes both briefings. A running generation is replaced. */
    fun generate() {
        job?.cancel()
        val useAi = _uiState.value.useAi
        _uiState.update { it.copy(isLoading = true) }
        job = viewModelScope.launch {
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
}
