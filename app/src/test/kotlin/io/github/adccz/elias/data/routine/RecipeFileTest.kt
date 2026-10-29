package io.github.adccz.elias.data.routine

import io.github.adccz.elias.domain.routine.Recipe
import io.github.adccz.elias.domain.routine.RecipeMeat
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The parser and the recipes shipped with the app (`assets/recipes.json`). */
class RecipeFileTest {
    private val parser = RoutineParser()
    private val shipped by lazy { parser.parseRecipes(File("src/main/assets/recipes.json").readText()) }

    /** Every word of a recipe, lowercase, so a stem matches the start of a word and nothing in the middle of one. */
    private fun Recipe.words(): List<String> = (listOf(name) + ingredients + steps)
        .joinToString(" ")
        .lowercase()
        .split(Regex("""[^\p{L}]+"""))
        .filter { it.isNotEmpty() }

    private fun Recipe.wordsStartingWith(stems: List<String>) = words().filter { word -> stems.any(word::startsWith) }

    @Test
    fun `has about forty recipes with different names`() {
        assertTrue(shipped.size >= 40, "${shipped.size} recipes")
        assertEquals(shipped.size, shipped.map { it.name.lowercase() }.toSet().size)
    }

    @Test
    fun `no recipe has pork, seafood or another forbidden food`() {
        shipped.forEach { recipe ->
            assertEquals(emptyList<String>(), recipe.wordsStartingWith(forbidden), "Forbidden food in ${recipe.id}")
        }
        // Two words that only mean something together.
        shipped.forEach { recipe ->
            val text = recipe.words().joinToString(" ")
            assertTrue("mořské plody" !in text && "plody moře" !in text, recipe.id)
        }
    }

    @Test
    fun `the check finds forbidden food in any form, but not a similar word`() {
        val recipe = Recipe(
            id = "x",
            name = "Těstoviny se slaninou",
            minutes = 10,
            servings = 1,
            meat = RecipeMeat.NONE,
            ingredients = listOf("100 g krevet", "1 pórek", "salát"),
            steps = listOf("Přidej Vepřové."),
        )

        assertEquals(listOf("slaninou", "krevet", "vepřové"), recipe.wordsStartingWith(forbidden))
    }

    @Test
    fun `a meatless recipe has no meat or fish in it`() {
        shipped.filter { it.meat == RecipeMeat.NONE }.forEach { recipe ->
            assertEquals(emptyList<String>(), recipe.wordsStartingWith(meatOrFish), "Meat in ${recipe.id}")
        }
    }

    @Test
    fun `a recipe with meat names it`() {
        val stems = mapOf(
            RecipeMeat.POULTRY to listOf("kuř", "krůt"),
            RecipeMeat.BEEF to listOf("hověz"),
            RecipeMeat.FISH to listOf("losos", "tresk", "tuňák", "pstruh", "makrel"),
        )
        shipped.filter { it.meat != RecipeMeat.NONE }.forEach { recipe ->
            assertTrue(recipe.wordsStartingWith(stems.getValue(recipe.meat)).isNotEmpty(), recipe.id)
        }
    }

    @Test
    fun `most recipes are quick and many are meatless`() {
        assertTrue(
            shipped.count {
                it.minutes <= 30
            } * 4 >= shipped.size * 3,
            "Fewer than three quarters within 30 min",
        )
        assertTrue(shipped.count { it.meat == RecipeMeat.NONE } * 5 >= shipped.size * 2, "Fewer than 40 % meatless")
    }

    @Test
    fun `reads a recipe`() {
        val recipes = parser.parseRecipes(
            """
            {"schemaVersion": 1, "recipes": [{"id": "a", "name": " Salát ", "minutes": 10, "servings": 2,
              "meat": "NONE", "ingredients": ["rajče", " "], "steps": ["Nakrájej."]}]}
            """.trimIndent(),
        )

        assertEquals(
            Recipe("a", "Salát", 10, 2, RecipeMeat.NONE, listOf("rajče"), listOf("Nakrájej.")),
            recipes.single(),
        )
    }

    @Test
    fun `rejects a broken file`() {
        fun file(meat: String = "NONE", minutes: Int = 10, steps: String = """["Nakrájej."]""") =
            """{"schemaVersion": 1, "recipes": [{"id": "a", "name": "Salát", "minutes": $minutes, "servings": 2,
              "meat": "$meat", "ingredients": ["rajče"], "steps": $steps}]}"""

        assertThrows<InvalidRoutineException> { parser.parseRecipes("[]") }
        assertThrows<InvalidRoutineException> { parser.parseRecipes(file(meat = "PORK")) }
        assertThrows<InvalidRoutineException> { parser.parseRecipes(file(minutes = 0)) }
        assertThrows<InvalidRoutineException> { parser.parseRecipes(file(steps = "[]")) }
    }

    private companion object {
        /** No pork, no seafood, no fish without scales, no rabbit, no gelatin (spec, M5). */
        val forbidden = listOf(
            // pork and pork products
            "vepř", "prase", "prasec", "slanin", "šunk", "sádl", "špek", "škvar", "bůč", "klobás", "salám", "párk",
            "párek", "uzenin", "jitrnic", "jelit", "tlačenk", "paštik", "krkovic", "panenk", "kotlet", "prosciutt",
            "pancett", "chorizo", "mortadel", "bacon", "ham",
            // seafood
            "krevet", "garnát", "mušl", "slávk", "ústřic", "hřebenatk", "kalamár", "chobotnic", "sépi", "krab",
            "humr", "langust", "račí", "raci",
            // fish without scales and other animals
            "sumec", "sumc", "úhoř", "žralok", "rejnok", "mečoun", "pangas", "králík", "králič",
            // gelatin
            "želatin", "aspik",
        )

        val meatOrFish = listOf(
            "maso", "masa", "kuř", "krůt", "hověz", "telec", "jehněč", "ryb", "losos", "tresk", "tuňák", "pstruh",
            "makrel", "kapr", "sleď", "sardink", "ančovič",
        )
    }
}
