package io.github.adccz.elias.data.routine

import io.github.adccz.elias.domain.routine.ExerciseAmount
import io.github.adccz.elias.domain.routine.ExerciseArea
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The parser and the exercises shipped with the app (`assets/exercises.json`). */
class ExerciseFileTest {
    private val parser = RoutineParser()
    private val shipped by lazy { parser.parseExercises(File("src/main/assets/exercises.json").readText()) }

    @Test
    fun `the shipped file has about twenty exercises in all four areas`() {
        assertTrue(shipped.size >= 20, "${shipped.size} exercises")
        ExerciseArea.entries.forEach { area ->
            assertTrue(shipped.count { it.area == area } >= 4, "Too few exercises in $area")
        }
    }

    @Test
    fun `the shipped texts are written for listening`() {
        val abbreviation = Regex("""\b\p{L}{1,4}\.\s*\p{Ll}""")
        shipped.forEach { exercise ->
            val text = "${exercise.name} ${exercise.description}"
            assertTrue(text.none { it.isDigit() }, "Digits in ${exercise.id}")
            assertTrue(!abbreviation.containsMatchIn(text), "An abbreviation in ${exercise.id}")
            assertTrue(exercise.description.trimEnd().endsWith("."), "${exercise.id} does not end a sentence")
        }
    }

    @Test
    fun `the shipped texts make no health claims`() {
        val claims = listOf("léč", "lék", "ulev", "zdrav", "prevenc", "bolest", "bolí", "terap", "nemoc", "zabrán")
        shipped.forEach { exercise ->
            val text = exercise.description.lowercase()
            claims.forEach { claim -> assertTrue(claim !in text, "\"$claim\" in ${exercise.id}") }
        }
    }

    @Test
    fun `the shipped amounts are short and easy to say`() {
        shipped.forEach { exercise ->
            when (val amount = exercise.amount) {
                is ExerciseAmount.Seconds -> assertTrue(amount.seconds in 10..60, exercise.id)

                // Up to twenty, so the spoken "-krát" stays one word ("patnáctkrát", not "dvacet pětkrát").
                is ExerciseAmount.Times -> assertTrue(amount.times in 2..20, exercise.id)
            }
        }
    }

    @Test
    fun `reads an exercise with times or seconds`() {
        val exercises = parser.parseExercises(
            """
            {"schemaVersion": 1, "exercises": [
              {"id": "a", "area": "LEGS_AND_EYES", "name": " Výpony ", "description": "Popis.", "times": 12},
              {"id": "b", "area": "BACK_AND_CHEST", "name": "Úklon", "description": "Popis.", "seconds": 15, "bothSides": true}
            ]}
            """.trimIndent(),
        )

        assertEquals("Výpony", exercises[0].name)
        assertEquals(ExerciseAmount.Times(12), exercises[0].amount)
        assertEquals(ExerciseAmount.Seconds(15), exercises[1].amount)
        assertTrue(exercises[1].bothSides)
    }

    @Test
    fun `rejects a broken file`() {
        fun file(entry: String) = """{"schemaVersion": 1, "exercises": [$entry]}"""

        assertThrows<InvalidRoutineException> { parser.parseExercises("{not json") }
        assertThrows<InvalidRoutineException> { parser.parseExercises("""{"schemaVersion": 2, "exercises": []}""") }
        assertThrows<InvalidRoutineException> {
            parser.parseExercises(file("""{"id": "a", "area": "FEET", "name": "A", "description": "B", "times": 5}"""))
        }
        assertThrows<InvalidRoutineException> {
            parser.parseExercises(
                file(
                    """{"id": "a", "area": "LEGS_AND_EYES", "name": "A", "description": "B", "times": 5, "seconds": 9}""",
                ),
            )
        }
        assertThrows<InvalidRoutineException> {
            parser.parseExercises(file("""{"id": "a", "area": "LEGS_AND_EYES", "name": "A", "description": "B"}"""))
        }
        assertThrows<InvalidRoutineException> {
            val entry = """{"id": "a", "area": "LEGS_AND_EYES", "name": "A", "description": "B", "times": 5}"""
            parser.parseExercises(file("$entry, $entry"))
        }
    }
}
