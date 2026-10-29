package io.github.adccz.elias.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.routine.Exercise
import io.github.adccz.elias.domain.routine.MorningRoutine
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Today's four exercises. [failed] is true when the exercise file could not be read. [speech] is what Elias is saying,
 * the same on every screen.
 */
data class ExercisesUiState(
    val day: LocalDate,
    val isLoaded: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
    val failed: Boolean = false,
    val speech: SpeechUiState = SpeechUiState.Idle,
)

/** The exercises at the computer for today (spec, M5): one from each area, the same all day, and Cvič se mnou. */
@HiltViewModel
class ExercisesViewModel @Inject constructor(routine: MorningRoutine, private val speech: SpeechController) :
    ViewModel() {
    private val content = MutableStateFlow(ExercisesUiState(routine.today()))

    val uiState: StateFlow<ExercisesUiState> = combine(content, speech.state) { content, speech ->
        content.copy(speech = speech)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, content.value)

    init {
        viewModelScope.launch {
            val result = routine.exercisesFor(content.value.day)
            content.value = content.value.copy(
                isLoaded = true,
                exercises = (result as? SourceResult.Success)?.data.orEmpty(),
                failed = result is SourceResult.Failure,
            )
        }
    }

    /** "Cvič se mnou": Elias leads the exercises through the playback service, on the media stream. */
    fun exerciseWithMe() = speech.exerciseWithMe()

    fun stop() = speech.stop()
}
