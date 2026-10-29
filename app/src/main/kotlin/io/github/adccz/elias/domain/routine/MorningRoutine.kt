package io.github.adccz.elias.domain.routine

import io.github.adccz.elias.domain.journal.JournalDay
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

/** The recipe of a day and the name of the next day's recipe, for shopping. */
data class DayRecipes(val today: Recipe, val tomorrow: Recipe)

/**
 * The exercises and the recipe of a day (spec, M5). A routine day starts at 4:00, like the journal's and the reading's,
 * so the screens do not change in the middle of a late evening.
 */
class MorningRoutine @Inject constructor(
    private val exercises: ExerciseRepository,
    private val recipes: RecipeRepository,
    private val clock: Clock,
) {
    fun today(): LocalDate = JournalDay.of(ZonedDateTime.now(clock))

    suspend fun exercisesFor(day: LocalDate): SourceResult<List<Exercise>> =
        exercises.exercises().map { Exercises.forDay(it, day) }

    suspend fun recipesFor(day: LocalDate): SourceResult<DayRecipes> = when (val result = recipes.recipes()) {
        is SourceResult.Failure -> result

        is SourceResult.Success -> {
            val today = Recipes.forDay(result.data, day)
            val tomorrow = Recipes.forDay(result.data, day.plusDays(1))
            if (today == null || tomorrow == null) {
                SourceResult.Failure(FailureReason.INVALID_DATA, "No recipes")
            } else {
                SourceResult.Success(DayRecipes(today, tomorrow), result.loadedAt)
            }
        }
    }

    private inline fun <T, R> SourceResult<T>.map(transform: (T) -> R): SourceResult<R> = when (this) {
        is SourceResult.Success -> SourceResult.Success(transform(data), loadedAt, fromCache)
        is SourceResult.Failure -> this
    }
}
