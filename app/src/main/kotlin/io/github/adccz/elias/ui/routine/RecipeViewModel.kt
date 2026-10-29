package io.github.adccz.elias.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.routine.DayRecipes
import io.github.adccz.elias.domain.routine.MorningRoutine
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Today's recipe and tomorrow's name. [failed] is true when the recipe file could not be read. */
data class RecipeUiState(
    val day: LocalDate,
    val isLoaded: Boolean = false,
    val recipes: DayRecipes? = null,
    val failed: Boolean = false,
)

/** The recipe of the day (spec, M5), with the name of tomorrow's recipe for shopping. */
@HiltViewModel
class RecipeViewModel @Inject constructor(routine: MorningRoutine) : ViewModel() {
    private val state = MutableStateFlow(RecipeUiState(routine.today()))
    val uiState: StateFlow<RecipeUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val result = routine.recipesFor(state.value.day)
            state.value = state.value.copy(
                isLoaded = true,
                recipes = (result as? SourceResult.Success)?.data,
                failed = result is SourceResult.Failure,
            )
        }
    }
}
