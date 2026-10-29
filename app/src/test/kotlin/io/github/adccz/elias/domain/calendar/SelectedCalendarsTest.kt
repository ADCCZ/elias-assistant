package io.github.adccz.elias.domain.calendar

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.testing.FakeCalendarSettings
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectedCalendarsTest {
    private val day = LocalDate.of(2026, 10, 29)

    private fun event(title: String, calendarId: Long?) = CalendarEvent(
        title = title,
        start = day.atTime(10, 0).atZone(PRAGUE_ZONE),
        end = day.atTime(11, 0).atZone(PRAGUE_ZONE),
        isAllDay = false,
        location = null,
        calendarName = null,
        calendarId = calendarId,
    )

    private val phone = object : CalendarRepository {
        override suspend fun eventsOn(date: LocalDate) = SourceResult.Success(
            listOf(event("Přednáška", 1), event("Narozeniny Petra", 5), event("Bez kalendáře", null)),
            Instant.EPOCH,
        )

        override suspend fun calendars() = SourceResult.Success(
            listOf(
                PhoneCalendar(1, "Škola", null, null, isShown = true),
                PhoneCalendar(5, "Narozeniny", null, null, isShown = true),
            ),
            Instant.EPOCH,
        )
    }

    private suspend fun SourceResult<List<CalendarEvent>>.titles() =
        (this as SourceResult.Success).data.map { it.title }

    @Test
    fun `leaves out the events of calendars that are switched off`() = runTest {
        val selected = SelectedCalendars(phone, FakeCalendarSettings(hidden = setOf(5)))

        assertEquals(listOf("Přednáška", "Bez kalendáře"), selected.eventsOn(day).titles())
        val week = selected.eventsFrom(day, days = 2) as SourceResult.Success
        assertEquals(listOf("Přednáška", "Bez kalendáře"), week.data.getValue(day.plusDays(1)).map { it.title })
    }

    @Test
    fun `still lists every calendar, so a switched-off one can be switched on again`() = runTest {
        val selected = SelectedCalendars(phone, FakeCalendarSettings(hidden = setOf(5)))

        assertEquals(listOf(1L, 5L), (selected.calendars() as SourceResult.Success).data.map { it.id })
    }

    @Test
    fun `reads everything when the settings cannot be read`() = runTest {
        val broken = object : CalendarSettingsRepository {
            override val topicCalendarIds: Flow<Set<Long>> = flow { error("broken file") }
            override val hiddenCalendarIds: Flow<Set<Long>> = flow { error("broken file") }

            override suspend fun setTopic(calendarId: Long, isTopic: Boolean) = Unit

            override suspend fun setRead(calendarId: Long, read: Boolean) = Unit
        }

        assertEquals(3, SelectedCalendars(phone, broken).eventsOn(day).titles().size)
    }

    @Test
    fun `passes a failure through`() = runTest {
        val noPermission = object : CalendarRepository {
            override suspend fun eventsOn(date: LocalDate) = SourceResult.Failure(FailureReason.NO_PERMISSION)
        }

        val result = SelectedCalendars(noPermission, FakeCalendarSettings(hidden = setOf(5))).eventsOn(day)

        assertEquals(SourceResult.Failure(FailureReason.NO_PERMISSION), result)
    }
}
