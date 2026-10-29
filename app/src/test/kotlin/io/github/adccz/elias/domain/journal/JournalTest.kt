package io.github.adccz.elias.domain.journal

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.testing.InMemoryJournal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class JournalTest {
    private val thursday = LocalDate.of(2026, 10, 29)

    private fun clockAt(text: String): Clock =
        Clock.fixed(LocalDateTime.parse(text).atZone(PRAGUE_ZONE).toInstant(), PRAGUE_ZONE)

    private fun entry(date: LocalDate, text: JournalText) = JournalEntry(date, text, Instant.EPOCH)

    @ParameterizedTest(name = "{0} belongs to {1}")
    @CsvSource(
        "2026-10-29T22:30, 2026-10-29",
        "2026-10-30T00:40, 2026-10-29",
        "2026-10-30T03:59, 2026-10-29",
        "2026-10-30T04:00, 2026-10-30",
        "2026-10-30T12:00, 2026-10-30",
    )
    fun `writing after midnight still belongs to the evening before`(now: String, day: String) {
        assertEquals(LocalDate.parse(day), JournalDay.of(LocalDateTime.parse(now).atZone(PRAGUE_ZONE)))
    }

    @Test
    fun `the night the clocks go back still counts for the day before`() {
        // 25 October 2026: 3:00 summer time becomes 2:00 winter time, so 2:30 happens twice.
        val secondHalfPast2 = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2026-10-25T02:30"),
            ZoneOffset.ofHours(1),
            PRAGUE_ZONE,
        )

        assertEquals(LocalDate.of(2026, 10, 24), JournalDay.of(secondHalfPast2))
    }

    @Test
    fun `saves a trimmed entry with the time it was written`() = runTest {
        val repository = InMemoryJournal()
        val clock = clockAt("2026-10-29T22:15")
        val journal = Journal(repository, clock)

        journal.save(thursday, JournalText(thought = "  Ticho pomáhá. ", toImprove = "Méně odkládat úkoly\n"))

        val stored = repository.stored(thursday)
        assertEquals(JournalText(thought = "Ticho pomáhá.", toImprove = "Méně odkládat úkoly"), stored?.text)
        assertEquals(clock.instant(), stored?.updatedAt)
    }

    @Test
    fun `an emptied entry is removed, not kept blank`() = runTest {
        val repository = InMemoryJournal(entry(thursday, JournalText(thought = "Něco")))
        val journal = Journal(repository, clockAt("2026-10-29T22:15"))

        journal.save(thursday, JournalText(thought = "   "))

        assertNull(repository.stored(thursday))
        assertFalse(journal.isWritten(thursday))
    }

    @Test
    fun `today is the journal day, not the calendar day`() {
        assertEquals(thursday, Journal(InMemoryJournal(), clockAt("2026-10-30T01:00")).today())
    }

    @Test
    fun `the morning reminds of yesterday's step, or of what to improve without one`() = runTest {
        val friday = thursday.plusDays(1)
        val withStep = InMemoryJournal(entry(thursday, JournalText(toImprove = "Číst víc", step = "Přečíst deset stran")))
        val withoutStep = InMemoryJournal(entry(thursday, JournalText(toImprove = "Číst víc")))
        val onlyThought = InMemoryJournal(entry(thursday, JournalText(thought = "Ticho")))
        val clock = clockAt("2026-10-30T07:00")

        assertEquals(JournalReminder("Přečíst deset stran", isStep = true), Journal(withStep, clock).reminderFor(friday))
        assertEquals(JournalReminder("Číst víc", isStep = false), Journal(withoutStep, clock).reminderFor(friday))
        assertNull(Journal(onlyThought, clock).reminderFor(friday))
        assertNull(Journal(InMemoryJournal(), clock).reminderFor(friday))
    }

    @Test
    fun `entries come newest first`() = runTest {
        val repository = InMemoryJournal(
            entry(thursday.minusDays(2), JournalText(thought = "A")),
            entry(thursday, JournalText(thought = "B")),
        )

        val days = Journal(repository, clockAt("2026-10-29T22:00")).entries.first().map { it.date }

        assertEquals(listOf(thursday, thursday.minusDays(2)), days)
        assertTrue(Journal(repository, clockAt("2026-10-29T22:00")).isWritten(thursday))
    }
}
