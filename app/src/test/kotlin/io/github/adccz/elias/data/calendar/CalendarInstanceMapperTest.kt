package io.github.adccz.elias.data.calendar

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalendarInstanceMapperTest {
    private val mapper = CalendarInstanceMapper()
    private val tuesday = LocalDate.of(2026, 10, 27) // winter time, UTC+1

    private fun utc(text: String) = Instant.parse(text).toEpochMilli()

    private fun local(text: String) = LocalDateTime.parse(text).atZone(PRAGUE_ZONE).toInstant().toEpochMilli()

    private fun row(
        title: String,
        begin: Long,
        end: Long,
        allDay: Boolean = false,
        canceled: Boolean = false,
        declined: Boolean = false,
    ) = CalendarInstanceRow(title, begin, end, allDay, location = null, calendarName = "Osobní", canceled, declined)

    private fun titlesOn(date: LocalDate, vararg rows: CalendarInstanceRow) =
        mapper.eventsOn(date, PRAGUE_ZONE, rows.toList()).map { it.title }

    @Test
    fun `an all-day event stored at UTC midnight belongs to its own day`() {
        val today = row("Svátek", utc("2026-10-27T00:00:00Z"), utc("2026-10-28T00:00:00Z"), allDay = true)

        val event = mapper.eventsOn(tuesday, PRAGUE_ZONE, listOf(today)).single()

        assertTrue(event.isAllDay)
        assertEquals(LocalDateTime.parse("2026-10-27T00:00").atZone(PRAGUE_ZONE), event.start)
        assertEquals(LocalDateTime.parse("2026-10-28T00:00").atZone(PRAGUE_ZONE), event.end)
    }

    @Test
    fun `yesterday's all-day event does not leak into today`() {
        // It ends at 00:00 UTC today, which is 01:00 in Prague. A naive overlap check would show it today.
        val yesterday = row("Včerejší výlet", utc("2026-10-26T00:00:00Z"), utc("2026-10-27T00:00:00Z"), allDay = true)

        assertEquals(emptyList<String>(), titlesOn(tuesday, yesterday))
    }

    @Test
    fun `a multi-day all-day event appears on every day it covers`() {
        val conference = row("Konference", utc("2026-10-26T00:00:00Z"), utc("2026-10-29T00:00:00Z"), allDay = true)

        assertEquals(listOf("Konference"), titlesOn(tuesday, conference))
        assertEquals(emptyList<String>(), titlesOn(tuesday.plusDays(2), conference))
    }

    @Test
    fun `timed events keep their local time`() {
        val lecture = row("Přednáška", local("2026-10-27T09:00"), local("2026-10-27T10:30"))

        val event = mapper.eventsOn(tuesday, PRAGUE_ZONE, listOf(lecture)).single()

        assertEquals(9, event.start.hour)
        assertEquals(30, event.end.minute)
    }

    @Test
    fun `an event over midnight counts, an event ending at midnight does not`() {
        val party = row("Oslava", local("2026-10-26T22:00"), local("2026-10-27T01:00"))
        val endsAtMidnight = row("Končí o půlnoci", local("2026-10-26T21:00"), local("2026-10-27T00:00"))
        val startsTomorrow = row("Zítra", local("2026-10-28T00:00"), local("2026-10-28T01:00"))

        assertEquals(listOf("Oslava"), titlesOn(tuesday, party, endsAtMidnight, startsTomorrow))
    }

    @Test
    fun `the 25-hour DST day ends at local midnight`() {
        val dstDay = LocalDate.of(2026, 10, 25)
        // 00:30 summer time on the 25th, 23:30 winter time on the 25th, and 00:30 on the 26th.
        val justAfterStart = row("Hned po půlnoci", utc("2026-10-24T22:30:00Z"), utc("2026-10-24T23:00:00Z"))
        val lateEvening = row("Pozdě večer", utc("2026-10-25T22:30:00Z"), utc("2026-10-25T22:45:00Z"))
        val nextDay = row("Další den", utc("2026-10-25T23:30:00Z"), utc("2026-10-26T00:00:00Z"))

        assertEquals(listOf("Hned po půlnoci", "Pozdě večer"), titlesOn(dstDay, justAfterStart, lateEvening, nextDay))
    }

    @Test
    fun `a zero-length reminder inside the day counts`() {
        val reminder = row("Zavolat", local("2026-10-27T09:00"), local("2026-10-27T09:00"))

        assertEquals(listOf("Zavolat"), titlesOn(tuesday, reminder))
    }

    @Test
    fun `canceled and declined events are left out`() {
        val canceled = row("Zrušeno", local("2026-10-27T09:00"), local("2026-10-27T10:00"), canceled = true)
        val declined = row("Odmítnuto", local("2026-10-27T11:00"), local("2026-10-27T12:00"), declined = true)

        assertEquals(emptyList<String>(), titlesOn(tuesday, canceled, declined))
    }

    @Test
    fun `all-day events come first, then by start time`() {
        val afternoon = row("Odpoledne", local("2026-10-27T14:00"), local("2026-10-27T15:00"))
        val morning = row(" Ráno ", local("2026-10-27T08:00"), local("2026-10-27T09:00"))
        val allDay = row("Celý den", utc("2026-10-27T00:00:00Z"), utc("2026-10-28T00:00:00Z"), allDay = true)

        assertEquals(listOf("Celý den", "Ráno", "Odpoledne"), titlesOn(tuesday, afternoon, morning, allDay))
    }
}
