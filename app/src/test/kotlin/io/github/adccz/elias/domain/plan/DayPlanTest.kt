package io.github.adccz.elias.domain.plan

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Uses a fictional plan. The real personal plan never appears in tests. */
class DayPlanTest {
    private fun date(text: String) = LocalDate.parse(text)

    private val plan = Plan(
        name = "Test",
        validFrom = date("2026-10-05"),
        validTo = date("2026-11-29"),
        phases = listOf(
            Phase("Rozjezd", date("2026-10-05"), date("2026-11-01")),
            Phase("Soustředění", date("2026-11-02"), date("2026-11-29")),
        ),
        dailyMinimum = listOf("Vstát do sedmi"),
        rules = listOf("Telefon mimo ložnici"),
        weeklyThemes = listOf(
            WeeklyTheme(date("2026-10-19"), "Trpělivost", "Nechat druhé dokončit myšlenku"),
            WeeklyTheme(date("2026-10-26"), "Vděčnost", "Zapsat tři dobré věci"),
        ),
        morningReadings = listOf(DatedText(date("2026-11-02"), "Př 1")),
        saturdayStudies = listOf(DatedText(date("2026-10-31"), "Ž 23")),
        recurringItems = listOf(
            RecurringItem("Angličtina", DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY),
            RecurringItem("Běh", setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)),
        ),
        oneTimeItems = listOf(DatedText(date("2026-11-15"), "Vrátit knihy do knihovny")),
        deadlines = listOf(
            Deadline(date("2026-10-28"), "Odevzdat první úkol", subject = "Databáze", mandatory = true),
            Deadline(date("2026-12-15"), "Obhajoba semestrální práce", subject = "Databáze"),
        ),
    )

    @Test
    fun `a weekly theme covers Monday to Sunday`() {
        assertEquals("Trpělivost", plan.dayPlan(date("2026-10-19")).weeklyTheme?.title) // Monday
        assertEquals("Trpělivost", plan.dayPlan(date("2026-10-25")).weeklyTheme?.title) // Sunday
        assertEquals("Vděčnost", plan.dayPlan(date("2026-10-26")).weeklyTheme?.title) // next Monday
    }

    @Test
    fun `phases include both their first and last day`() {
        assertEquals("Rozjezd", plan.dayPlan(date("2026-11-01")).phase?.name)
        assertEquals("Soustředění", plan.dayPlan(date("2026-11-02")).phase?.name)
    }

    @Test
    fun `recurring items follow the day of the week`() {
        val tuesday = plan.dayPlan(date("2026-10-27"))
        val saturday = plan.dayPlan(date("2026-10-31"))

        assertEquals(listOf("Angličtina", "Běh"), tuesday.items.map { it.title })
        assertTrue(saturday.items.isEmpty())
    }

    @Test
    fun `one-time items, readings and studies appear only on their date`() {
        val libraryDay = plan.dayPlan(date("2026-11-15")).items.map { it.title }
        val dayBefore = plan.dayPlan(date("2026-11-14")).items.map { it.title }

        assertTrue("Vrátit knihy do knihovny" in libraryDay)
        assertFalse("Vrátit knihy do knihovny" in dayBefore)
        assertEquals("Př 1", plan.dayPlan(date("2026-11-02")).morningReading)
        assertNull(plan.dayPlan(date("2026-11-03")).morningReading)
        assertEquals("Ž 23", plan.dayPlan(date("2026-10-31")).saturdayStudy)
    }

    @Test
    fun `reports deadlines for today and tomorrow`() {
        val tuesday = plan.dayPlan(date("2026-10-27"))
        val wednesday = plan.dayPlan(date("2026-10-28"))

        assertEquals("Odevzdat první úkol", tuesday.deadlinesTomorrow.single().title)
        assertEquals("Odevzdat první úkol", wednesday.deadlinesToday.single().title)
    }

    @Test
    fun `outside the plan only deadlines remain`() {
        val day = plan.dayPlan(date("2026-12-14"))

        assertFalse(day.isPlanValid)
        assertNull(day.phase)
        assertTrue(day.items.isEmpty())
        assertTrue(day.dailyMinimum.isEmpty())
        assertEquals("Obhajoba semestrální práce", day.deadlinesTomorrow.single().title)
    }
}
