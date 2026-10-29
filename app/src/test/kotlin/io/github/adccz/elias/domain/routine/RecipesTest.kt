package io.github.adccz.elias.domain.routine

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecipesTest {
    private val recipes = (1..5).map { Recipe("r$it", "Recept $it", 20, 2, RecipeMeat.NONE, listOf("a"), listOf("b")) }
    private val thursday = LocalDate.of(2026, 10, 29)

    @Test
    fun `every recipe has its day before any comes again`() {
        val fiveDays = (0L..4L).map { Recipes.forDay(recipes, thursday.plusDays(it)) }

        assertEquals(recipes.toSet(), fiveDays.toSet())
        assertEquals(Recipes.forDay(recipes, thursday), Recipes.forDay(recipes, thursday.plusDays(5)))
    }

    @Test
    fun `tomorrow's recipe is the next one`() {
        val today = Recipes.forDay(recipes, thursday)!!
        val tomorrow = Recipes.forDay(recipes, thursday.plusDays(1))!!

        assertEquals((recipes.indexOf(today) + 1) % recipes.size, recipes.indexOf(tomorrow))
    }

    @Test
    fun `no recipes, no recipe of the day`() {
        assertNull(Recipes.forDay(emptyList(), thursday))
    }
}
