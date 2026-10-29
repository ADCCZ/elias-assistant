package io.github.adccz.elias.domain.routine

import io.github.adccz.elias.domain.briefing.lowerFirst

/** One step of "Cvič se mnou": something Elias says, or time to exercise in silence. */
sealed interface ExerciseStep {
    data class Say(val text: String) : ExerciseStep

    data class Wait(val millis: Long) : ExerciseStep
}

/**
 * "Cvič se mnou" (spec, M5): Elias reads the exercises one by one, leaves time to do each of them and announces the
 * other side. The times are fixed: a hold takes its seconds, a repetition three seconds.
 */
object ExerciseScript {
    /** How long one repetition takes: slow and calm. */
    const val SECONDS_PER_REPETITION = 3

    /** A breath between the opening words and the first exercise. */
    const val AFTER_INTRO_MILLIS = 1_500L

    private const val MILLIS_PER_SECOND = 1_000L

    fun of(exercises: List<Exercise>): List<ExerciseStep> {
        if (exercises.isEmpty()) return emptyList()
        val steps = mutableListOf<ExerciseStep>(
            ExerciseStep.Say("Cvičíme spolu. ${Exercises.SAFETY}"),
            ExerciseStep.Wait(AFTER_INTRO_MILLIS),
        )
        exercises.forEachIndexed { index, exercise ->
            val work = workMillis(exercise)
            steps += ExerciseStep.Say(
                "${ordinal(index)} cvik: ${lowerFirst(exercise.name)}. ${exercise.description} " +
                    "${Exercises.spokenAmount(exercise)}. Začni.",
            )
            steps += ExerciseStep.Wait(work)
            if (exercise.bothSides) {
                steps += ExerciseStep.Say("Druhá strana.")
                steps += ExerciseStep.Wait(work)
            }
        }
        steps += ExerciseStep.Say("To byly dnešní cviky.")
        return steps
    }

    /** The time for one side of [exercise]. */
    fun workMillis(exercise: Exercise): Long = when (val amount = exercise.amount) {
        is ExerciseAmount.Seconds -> amount.seconds * MILLIS_PER_SECOND
        is ExerciseAmount.Times -> amount.times * SECONDS_PER_REPETITION * MILLIS_PER_SECOND
    }

    /** "První", "Druhý"…: the exercise is masculine ("cvik"). */
    private fun ordinal(index: Int): String = ordinals.getOrElse(index) { "Další" }

    private val ordinals = listOf("První", "Druhý", "Třetí", "Čtvrtý", "Pátý", "Šestý", "Sedmý", "Osmý")
}
