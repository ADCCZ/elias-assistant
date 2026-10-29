package io.github.adccz.elias.testing

import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.routine.Exercise
import io.github.adccz.elias.domain.routine.ExerciseAmount
import io.github.adccz.elias.domain.routine.ExerciseArea
import io.github.adccz.elias.domain.routine.ExerciseRepository
import io.github.adccz.elias.domain.routine.Recipe
import io.github.adccz.elias.domain.routine.RecipeMeat
import io.github.adccz.elias.domain.routine.RecipeRepository
import java.time.Instant

/** Fictional exercises and recipes; `null` makes the source fail like a broken file. */
class FixedRoutineContent(
    private val exercises: List<Exercise>? = TestRoutine.exercises,
    private val recipes: List<Recipe>? = TestRoutine.recipes,
) : ExerciseRepository,
    RecipeRepository {
    override suspend fun exercises(): SourceResult<List<Exercise>> = exercises
        ?.let { SourceResult.Success(it, Instant.EPOCH) }
        ?: SourceResult.Failure(FailureReason.INVALID_DATA)

    override suspend fun recipes(): SourceResult<List<Recipe>> = recipes
        ?.let { SourceResult.Success(it, Instant.EPOCH) }
        ?: SourceResult.Failure(FailureReason.INVALID_DATA)
}

/** One exercise per area and two recipes, so every day's pick is known. */
object TestRoutine {
    val exercises = listOf(
        Exercise(
            "neck",
            ExerciseArea.NECK_AND_SHOULDERS,
            "Kroužení rameny",
            "Krouž rameny.",
            ExerciseAmount.Times(10),
            false,
        ),
        Exercise(
            "back",
            ExerciseArea.BACK_AND_CHEST,
            "Rotace trupu vsedě",
            "Otoč trup.",
            ExerciseAmount.Seconds(20),
            true,
        ),
        Exercise(
            "hands",
            ExerciseArea.HANDS_AND_WRISTS,
            "Pěst a prsty",
            "Sevři pěst.",
            ExerciseAmount.Times(10),
            false,
        ),
        Exercise(
            "eyes",
            ExerciseArea.LEGS_AND_EYES,
            "Pohled do dálky",
            "Podívej se z okna.",
            ExerciseAmount.Seconds(20),
            false,
        ),
    )

    val recipes = listOf(
        Recipe("salad", "Čočkový salát", 20, 2, RecipeMeat.NONE, listOf("čočka"), listOf("Promíchej.")),
        Recipe("curry", "Kuřecí kari s rýží", 30, 2, RecipeMeat.POULTRY, listOf("kuře"), listOf("Uvař.")),
    )
}
