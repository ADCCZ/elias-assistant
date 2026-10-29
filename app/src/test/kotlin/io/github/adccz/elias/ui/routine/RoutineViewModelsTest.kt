package io.github.adccz.elias.ui.routine

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.routine.MorningRoutine
import io.github.adccz.elias.domain.routine.Recipes
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.testing.FakeVoicePlayer
import io.github.adccz.elias.testing.FixedRoutineContent
import io.github.adccz.elias.testing.MainDispatcherExtension
import io.github.adccz.elias.testing.TestRoutine
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineViewModelsTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    // Friday 30 October 2026, 1:00: the routine day is still Thursday.
    private val clock = Clock.fixed(
        LocalDateTime.parse("2026-10-30T01:00").atZone(PRAGUE_ZONE).toInstant(),
        PRAGUE_ZONE,
    )
    private val thursday = LocalDate.of(2026, 10, 29)

    private val player = FakeVoicePlayer()

    private fun routine(content: FixedRoutineContent = FixedRoutineContent()) = MorningRoutine(content, content, clock)

    @Test
    fun `the exercises screen shows one exercise from each area`() = runTest {
        val viewModel = ExercisesViewModel(routine(), SpeechController(player))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(thursday, state.day)
        assertTrue(state.isLoaded)
        assertFalse(state.failed)
        assertEquals(TestRoutine.exercises, state.exercises)
    }

    @Test
    fun `the exercises screen says when the file is broken`() = runTest {
        val viewModel = ExercisesViewModel(routine(FixedRoutineContent(exercises = null)), SpeechController(player))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.failed)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.exercises)
    }

    @Test
    fun `Cvič se mnou asks the playback service and shows what it does`() = runTest {
        val viewModel = ExercisesViewModel(routine(), SpeechController(player))
        advanceUntilIdle()

        viewModel.exerciseWithMe()
        advanceUntilIdle()

        assertEquals(listOf<BriefingRequest>(BriefingRequest.Exercises), player.requests)
        assertEquals(SpeechUiState.Preparing(BriefingKind.EXERCISES), viewModel.uiState.value.speech)

        viewModel.stop()
        advanceUntilIdle()

        assertEquals(1, player.stops)
        assertEquals(SpeechUiState.Idle, viewModel.uiState.value.speech)
    }

    @Test
    fun `the recipe screen shows today's recipe and tomorrow's`() = runTest {
        val viewModel = RecipeViewModel(routine())
        advanceUntilIdle()

        val recipes = viewModel.uiState.value.recipes!!
        assertEquals(Recipes.forDay(TestRoutine.recipes, thursday), recipes.today)
        assertEquals(Recipes.forDay(TestRoutine.recipes, thursday.plusDays(1)), recipes.tomorrow)
        assertTrue(recipes.today != recipes.tomorrow)
    }

    @Test
    fun `the recipe screen says when there are no recipes`() = runTest {
        val broken = RecipeViewModel(routine(FixedRoutineContent(recipes = null)))
        val empty = RecipeViewModel(routine(FixedRoutineContent(recipes = emptyList())))
        advanceUntilIdle()

        assertTrue(broken.uiState.value.failed)
        assertNull(broken.uiState.value.recipes)
        assertTrue(empty.uiState.value.failed)
    }
}
