package io.github.adccz.elias.domain.routine

import io.github.adccz.elias.domain.model.SourceResult
import java.time.LocalDate

/** What meat a recipe has. Only poultry, beef and fish with scales: no pork and no seafood (spec, M5). */
enum class RecipeMeat { NONE, POULTRY, BEEF, FISH }

/** A simple recipe written in our own words. [ingredients] and [steps] are read on the screen, only [name] is spoken. */
data class Recipe(
    val id: String,
    val name: String,
    val minutes: Int,
    val servings: Int,
    val meat: RecipeMeat,
    val ingredients: List<String>,
    val steps: List<String>,
)

/** The recipes shipped with the app (`assets/recipes.json`). */
interface RecipeRepository {
    suspend fun recipes(): SourceResult<List<Recipe>>
}

object Recipes {
    /**
     * The recipe of [day]: the recipes take turns in the order of the file, so none comes again before all the others
     * have had their day. Null without recipes.
     */
    fun forDay(all: List<Recipe>, day: LocalDate): Recipe? =
        if (all.isEmpty()) null else all[Math.floorMod(day.toEpochDay(), all.size.toLong()).toInt()]
}
