package io.github.adccz.elias.domain.calendar

import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * One occurrence of a calendar event. For an all-day event, [start] and [end] are local midnights
 * and [end] is exclusive (a one-day event on 27 October ends at 28 October 00:00).
 *
 * [calendarId] is the id of the phone calendar the event belongs to. It tells topic calendars apart (M4.5).
 */
data class CalendarEvent(
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val isAllDay: Boolean,
    val location: String?,
    val calendarName: String?,
    val calendarId: Long? = null,
)

/**
 * One calendar on the phone, such as the Google calendar "Škola".
 *
 * [color] is the ARGB color the calendar app uses, null when unknown. [isShown] is false when the calendar is hidden
 * or not synced in the phone's calendar app: then its events never reach Elias.
 */
data class PhoneCalendar(
    val id: Long,
    val name: String,
    val accountName: String?,
    val color: Int?,
    val isShown: Boolean,
)

/** Reads events from the calendars synced to the phone. Implemented in the data layer. */
interface CalendarRepository {
    /** Events that take place (at least partly) on [date], all-day events first, then by start time. */
    suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>>

    /**
     * The events of [days] days starting with [firstDay], one list per day (the same lists [eventsOn] gives).
     * The phone's implementation reads them in one query, this default asks day by day.
     */
    suspend fun eventsFrom(firstDay: LocalDate, days: Int): SourceResult<Map<LocalDate, List<CalendarEvent>>> {
        val byDay = linkedMapOf<LocalDate, List<CalendarEvent>>()
        var loadedAt: Instant? = null
        for (offset in 0 until days) {
            val date = firstDay.plusDays(offset.toLong())
            when (val result = eventsOn(date)) {
                is SourceResult.Failure -> return result

                is SourceResult.Success -> {
                    byDay[date] = result.data
                    loadedAt = loadedAt ?: result.loadedAt
                }
            }
        }
        return loadedAt?.let { SourceResult.Success(byDay, it) }
            ?: SourceResult.Failure(FailureReason.INVALID_DATA, "No days asked for")
    }

    /** The calendars on the phone, in the order the provider gives them. */
    suspend fun calendars(): SourceResult<List<PhoneCalendar>> =
        SourceResult.Failure(FailureReason.UNKNOWN, "This calendar source cannot list calendars")
}
