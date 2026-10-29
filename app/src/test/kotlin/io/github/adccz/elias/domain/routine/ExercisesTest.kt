package io.github.adccz.elias.domain.routine

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ExercisesTest {
    private fun exercise(
        id: String,
        area: ExerciseArea,
        amount: ExerciseAmount = ExerciseAmount.Times(10),
        both: Boolean = false,
    ) = Exercise(id, area, "Cvik $id", "Popis.", amount, both)

    /** Three exercises in every area: "neck0".."neck2" and so on. */
    private val all = ExerciseArea.entries.flatMap { area ->
        (0..2).map { exercise("${area.name.take(4).lowercase()}$it", area) }
    }
    private val thursday = LocalDate.of(2026, 10, 29)

    @Test
    fun `picks one exercise from each area in the order of the areas`() {
        val today = Exercises.forDay(all, thursday)

        assertEquals(ExerciseArea.entries, today.map { it.area })
    }

    @Test
    fun `the same day gives the same exercises and the next day other ones`() {
        val today = Exercises.forDay(all, thursday)

        assertEquals(today, Exercises.forDay(all, thursday))
        val tomorrow = Exercises.forDay(all, thursday.plusDays(1))
        today.zip(tomorrow).forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test
    fun `goes through all exercises of an area before repeating`() {
        val neckExercises = (0L..2L).map { Exercises.forDay(all, thursday.plusDays(it)).first().id }

        assertEquals(setOf("neck0", "neck1", "neck2"), neckExercises.toSet())
        assertEquals(Exercises.forDay(all, thursday), Exercises.forDay(all, thursday.plusDays(3)))
    }

    @Test
    fun `an area without exercises is left out`() {
        val withoutEyes = all.filterNot { it.area == ExerciseArea.LEGS_AND_EYES }

        assertEquals(3, Exercises.forDay(withoutEyes, thursday).size)
        assertEquals(emptyList<Exercise>(), Exercises.forDay(emptyList(), thursday))
    }

    @ParameterizedTest(name = "{0} {1}, both sides {2}: {3}")
    @CsvSource(
        "times, 10, false, Desetkrát",
        "times, 4, false, Čtyřikrát",
        "times, 1, false, Jednou",
        "seconds, 20, true, 'Dvacet sekund, na každou stranu'",
        "seconds, 15, false, Patnáct sekund",
        "seconds, 45, false, Pětačtyřicet sekund",
    )
    fun `says the amount in words`(unit: String, value: Int, bothSides: Boolean, expected: String) {
        val amount = if (unit == "times") ExerciseAmount.Times(value) else ExerciseAmount.Seconds(value)

        assertEquals(
            expected,
            Exercises.spokenAmount(exercise("x", ExerciseArea.NECK_AND_SHOULDERS, amount, bothSides)),
        )
    }
}
