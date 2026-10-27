package io.github.adccz.elias.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.overview.TodayOverview
import io.github.adccz.elias.domain.overview.TodayOverviewLoader
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DataUiState {
    /** The first load is running and there is nothing to show yet. */
    data object Loading : DataUiState

    /** Data is shown. [isRefreshing] is true while a newer load runs in the background. */
    data class Loaded(val overview: TodayOverview, val isRefreshing: Boolean) : DataUiState
}

@HiltViewModel
class DataViewModel @Inject constructor(private val loader: TodayOverviewLoader) : ViewModel() {
    private val _uiState = MutableStateFlow<DataUiState>(DataUiState.Loading)
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** Loads everything again. A second call while loading is ignored. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        _uiState.update { current ->
            if (current is DataUiState.Loaded) current.copy(isRefreshing = true) else DataUiState.Loading
        }
        loadJob = viewModelScope.launch {
            _uiState.value = DataUiState.Loaded(loader.load(), isRefreshing = false)
        }
    }

    /**
     * Called when the screen becomes visible again, for example after the user allowed calendar access
     * in system settings. Reloads only if the calendar was missing that permission.
     */
    fun onScreenResumed() {
        val calendar = (uiState.value as? DataUiState.Loaded)?.overview?.calendar
        if (calendar is SourceResult.Failure && calendar.reason == FailureReason.NO_PERMISSION) refresh()
    }
}
