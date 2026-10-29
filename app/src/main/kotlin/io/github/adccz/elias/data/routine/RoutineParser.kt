package io.github.adccz.elias.data.routine

import io.github.adccz.elias.domain.routine.Exercise
import io.github.adccz.elias.domain.routine.ExerciseAmount
import io.github.adccz.elias.domain.routine.ExerciseArea
import io.github.adccz.elias.domain.routine.Recipe
import io.github.adccz.elias.domain.routine.RecipeMeat
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A routine file is wrong. The message says what and where, so it can be fixed by hand. */
class InvalidRoutineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reads the hand-written routine files in the assets (schema version 1). */
class RoutineParser @Inject constructor() {
    // Unknown keys are errors on purpose: a typo in a hand-written file should not be silently ignored.
    private val json = Json { ignoreUnknownKeys = false }

    /** `exercises.json`. @throws InvalidRoutineException when the JSON or a value is wrong. */
    fun parseExercises(text: String): List<Exercise> {
        val file = decode(text) { json.decodeFromString<ExerciseFile>(it) }
        checkSchema(file.schemaVersion)
        checkUniqueIds(file.exercises.map { it.id })
        return file.exercises.map { it.toExercise() }
    }

    private fun ExerciseEntry.toExercise(): Exercise {
        val area = ExerciseArea.entries.firstOrNull { it.name == area }
            ?: throw InvalidRoutineException("Exercise $id: unknown area $area")
        val amount = when {
            seconds != null && times == null && seconds > 0 -> ExerciseAmount.Seconds(seconds)
            times != null && seconds == null && times > 0 -> ExerciseAmount.Times(times)
            else -> throw InvalidRoutineException("Exercise $id needs either seconds or times, a positive number")
        }
        if (name.isBlank() || description.isBlank()) throw InvalidRoutineException("Exercise $id has an empty text")
        return Exercise(id, area, name.trim(), description.trim(), amount, bothSides)
    }

    /** `recipes.json`. @throws InvalidRoutineException when the JSON or a value is wrong. */
    fun parseRecipes(text: String): List<Recipe> {
        val file = decode(text) { json.decodeFromString<RecipeFile>(it) }
        checkSchema(file.schemaVersion)
        checkUniqueIds(file.recipes.map { it.id })
        return file.recipes.map { it.toRecipe() }
    }

    private fun RecipeEntry.toRecipe(): Recipe {
        val meat = RecipeMeat.entries.firstOrNull { it.name == meat }
            ?: throw InvalidRoutineException("Recipe $id: unknown meat $meat")
        if (minutes <= 0 ||
            servings <= 0
        ) {
            throw InvalidRoutineException("Recipe $id needs positive minutes and servings")
        }
        val ingredients = ingredients.map { it.trim() }.filter { it.isNotEmpty() }
        val steps = steps.map { it.trim() }.filter { it.isNotEmpty() }
        if (name.isBlank() || ingredients.isEmpty() || steps.isEmpty()) {
            throw InvalidRoutineException("Recipe $id needs a name, ingredients and steps")
        }
        return Recipe(id, name.trim(), minutes, servings, meat, ingredients, steps)
    }

    private fun <T> decode(text: String, read: (String) -> T): T = try {
        read(text)
    } catch (e: IllegalArgumentException) {
        throw InvalidRoutineException("Not valid JSON for schema $SUPPORTED_SCHEMA_VERSION: ${e.message}", e)
    }

    private fun checkSchema(version: Int) {
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw InvalidRoutineException("Unsupported schemaVersion $version, expected $SUPPORTED_SCHEMA_VERSION")
        }
    }

    private fun checkUniqueIds(ids: List<String>) {
        val repeated = ids.groupBy { it }.filterValues { it.size > 1 }.keys
        if (repeated.isNotEmpty()) throw InvalidRoutineException("Repeated ids: ${repeated.joinToString()}")
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

@Serializable
private class ExerciseFile(val schemaVersion: Int, val exercises: List<ExerciseEntry>)

@Serializable
private class ExerciseEntry(
    val id: String,
    val area: String,
    val name: String,
    val description: String,
    val seconds: Int? = null,
    val times: Int? = null,
    val bothSides: Boolean = false,
)

@Serializable
private class RecipeFile(val schemaVersion: Int, val recipes: List<RecipeEntry>)

@Serializable
private class RecipeEntry(
    val id: String,
    val name: String,
    val minutes: Int,
    val servings: Int,
    val meat: String,
    val ingredients: List<String>,
    val steps: List<String>,
)
