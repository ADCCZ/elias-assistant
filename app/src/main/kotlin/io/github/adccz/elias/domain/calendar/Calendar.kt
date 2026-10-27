package io.github.adccz.elias.domain.calendar

import io.github.adccz.elias.domain.model.SourceResult
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * One occurrence of a calendar event. For an all-day event, [start] and [end] are local midnights
 * and [end] is exclusive (a one-day event on 27 October ends at 28 October 00:00).
 */
data class CalendarEvent(
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val isAllDay: Boolean,
    val location: String?,
    val calendarName: String?,
)

/** Reads events from the calendars synced to the phone. Implemented in the data layer. */
interface CalendarRepository {
    /** Events that take place (at least partly) on [date], all-day events first, then by start time. */
    suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>>
}
