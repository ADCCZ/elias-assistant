package io.github.adccz.elias.domain.routine

import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.speech.CzechNumbers
import io.github.adccz.elias.domain.speech.Gender
import java.time.LocalDate

/** The four areas of the exercises at the computer (spec, M5), in the order they are done. */
enum class ExerciseArea(val title: String) {
    NECK_AND_SHOULDERS("Krk a ramena"),
    BACK_AND_CHEST("Záda a hrudník"),
    HANDS_AND_WRISTS("Ruce a zápěstí"),
    LEGS_AND_EYES("Nohy a oči"),
}

/** How much of an exercise: keep at it for a while, or repeat it. On each side when the exercise has two. */
sealed interface ExerciseAmount {
    data class Seconds(val seconds: Int) : ExerciseAmount

    data class Times(val times: Int) : ExerciseAmount
}

/**
 * A stretching exercise for a break at the computer. [description] is written for listening: no digits, no
 * abbreviations. With [bothSides] the [amount] is for each side.
 */
data class Exercise(
    val id: String,
    val area: ExerciseArea,
    val name: String,
    val description: String,
    val amount: ExerciseAmount,
    val bothSides: Boolean,
)

/** The exercises shipped with the app (`assets/exercises.json`). */
interface ExerciseRepository {
    suspend fun exercises(): SourceResult<List<Exercise>>
}

object Exercises {
    /** Said before the exercises and shown with them. No health claims, only this (spec, M5). */
    const val SAFETY = "Cvič pomalu a jen do příjemného tahu, když něco bolí, cvik vynech."

    /**
     * One exercise from each area for [day], picked by the date without chance: the same all day, the next one in the
     * area tomorrow. An area without exercises is left out.
     */
    fun forDay(all: List<Exercise>, day: LocalDate): List<Exercise> = ExerciseArea.entries.mapNotNull { area ->
        val inArea = all.filter { it.area == area }
        if (inArea.isEmpty()) null else inArea[Math.floorMod(day.toEpochDay(), inArea.size.toLong()).toInt()]
    }

    /** "Desetkrát", "Dvacet sekund", with ", na každou stranu" for an exercise with two sides. */
    fun spokenAmount(exercise: Exercise): String {
        val amount = when (val amount = exercise.amount) {
            is ExerciseAmount.Times -> if (amount.times == 1) "jednou" else CzechNumbers.cardinal(amount.times) + "krát"

            is ExerciseAmount.Seconds ->
                CzechNumbers.count(amount.seconds, "sekunda", "sekundy", "sekund", Gender.FEMININE)
        }
        val sides = if (exercise.bothSides) ", na každou stranu" else ""
        return amount.replaceFirstChar { it.uppercase() } + sides
    }
}
