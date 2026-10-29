package io.github.adccz.elias.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.routine.Exercise
import io.github.adccz.elias.domain.routine.MorningRoutine
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Today's four exercises. [failed] is true when the exercise file could not be read. */
data class ExercisesUiState(
    val day: LocalDate,
    val isLoaded: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
    val failed: Boolean = false,
)

/** The exercises at the computer for today (spec, M5): one from each area, the same all day. */
@HiltViewModel
class ExercisesViewModel @Inject constructor(routine: MorningRoutine) : ViewModel() {
    private val state = MutableStateFlow(ExercisesUiState(routine.today()))
    val uiState: StateFlow<ExercisesUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val result = routine.exercisesFor(state.value.day)
            state.value = state.value.copy(
                isLoaded = true,
                exercises = (result as? SourceResult.Success)?.data.orEmpty(),
                failed = result is SourceResult.Failure,
            )
        }
    }
}
