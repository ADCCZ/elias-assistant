package io.github.adccz.elias.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the home screen shows. The screen only renders this, it holds no logic. */
data class HomeUiState(val today: LocalDate)

/**
 * State holder for the home screen.
 *
 * Hilt creates it and passes the app [Clock] into the constructor (constructor injection).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(clock: Clock) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
