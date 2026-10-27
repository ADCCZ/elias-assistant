package io.github.adccz.elias.data.plan

import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlanParserTest {
    private val parser = PlanParser()

    private fun planJson(extraFields: String = "") = """
        {
          "schemaVersion": 1,
          "name": "Test",
          "validFrom": "2026-10-19",
          "validTo": "2026-11-30"
          $extraFields
        }
    """.trimIndent()

    @Test
    fun `parses a minimal plan and fills empty lists`() {
        val plan = parser.parse(planJson())

        assertEquals("Test", plan.name)
        assertEquals(LocalDate.of(2026, 10, 19), plan.validFrom)
        assertTrue(plan.phases.isEmpty())
        assertTrue(plan.deadlines.isEmpty())
    }

    @Test
    fun `parses recurring days case-insensitively`() {
        val plan = parser.parse(planJson(""", "recurring": [{ "title": "Běh", "days": ["tuesday", "SUNDAY"] }]"""))

        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY), plan.recurringItems.single().days)
    }

    @Test
    fun `reads the optional reflection question of a weekly theme`() {
        val plan = parser.parse(
            planJson(
                """, "weeklyThemes": [
                    { "weekStart": "2026-10-19", "title": "A", "task": "B", "reflectionQuestion": "Jak to šlo?" },
                    { "weekStart": "2026-10-26", "title": "C", "task": "D" }
                ]""",
            ),
        )

        assertEquals("Jak to šlo?", plan.weeklyThemes[0].reflectionQuestion)
        assertEquals(null, plan.weeklyThemes[1].reflectionQuestion)
    }

    @Test
    fun `rejects an invalid date and names the field`() {
        val error = assertThrows<InvalidPlanException> {
            parser.parse(planJson(""", "deadlines": [{ "date": "2026-13-01", "title": "X" }]"""))
        }

        assertTrue("deadlines.date" in error.message.orEmpty()) { error.message }
    }

    @Test
    fun `rejects a weekly theme that does not start on Monday`() {
        assertThrows<InvalidPlanException> {
            parser.parse(planJson(""", "weeklyThemes": [{ "weekStart": "2026-10-20", "title": "X", "task": "Y" }]"""))
        }
    }

    @Test
    fun `rejects unknown keys so typos are noticed`() {
        assertThrows<InvalidPlanException> { parser.parse(planJson(""", "deadlinez": []""")) }
    }

    @Test
    fun `rejects an unsupported schema version`() {
        assertThrows<InvalidPlanException> {
            parser.parse(planJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        }
    }

    @Test
    fun `rejects a plan that ends before it starts`() {
        assertThrows<InvalidPlanException> { parser.parse(planJson().replace("2026-11-30", "2026-10-01")) }
    }

    @Test
    fun `the bundled sample plan is valid`() {
        val plan = parser.parse(File("src/main/assets/plan.example.json").readText())

        assertTrue(plan.weeklyThemes.isNotEmpty())
    }

    /** The personal plan is git-ignored, so this test runs only on a machine that has it. */
    @Test
    fun `the personal plan is valid when present`() {
        val file = File("src/main/assets/plan.json")
        assumeTrue(file.exists(), "No personal plan on this machine")

        parser.parse(file.readText())
    }
}
