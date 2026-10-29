package io.github.adccz.elias.data.calendar

import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

/** One row of `CalendarContract.Instances`, copied out of the database cursor without any interpretation. */
data class CalendarInstanceRow(
    val title: String?,
    val beginMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val location: String?,
    val calendarName: String?,
    val isCanceled: Boolean,
    val isDeclinedByMe: Boolean,
    val calendarId: Long? = null,
)

/** One row of `CalendarContract.Calendars`, copied out of the database cursor without any interpretation. */
data class CalendarRow(
    val id: Long,
    val displayName: String?,
    val accountName: String?,
    val color: Int?,
    val isVisible: Boolean,
    val isSynced: Boolean,
)

/**
 * Turns raw calendar rows into the events of one local day.
 *
 * The tricky part: Android stores all-day events at **midnight UTC** ("If allDay is set to 1 eventTimezone
 * must be UTC and the time must correspond to a midnight boundary"). Yesterday's all-day event therefore
 * ends at 00:00 UTC today, which is 01:00 or 02:00 in Prague. A naive "overlaps today in Prague" check would
 * show it today as well. So all-day events are compared by their UTC date, timed events by their instants.
 */
class CalendarInstanceMapper @Inject constructor() {
    fun eventsOn(date: LocalDate, zone: ZoneId, rows: List<CalendarInstanceRow>): List<CalendarEvent> {
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

        return rows
            .filterNot { it.isCanceled || it.isDeclinedByMe }
            .mapNotNull { row ->
                if (row.isAllDay) allDayEvent(row, date, zone) else timedEvent(row, dayStart, dayEnd, zone)
            }
            .sortedWith(compareByDescending<CalendarEvent> { it.isAllDay }.thenBy { it.start })
    }

    private fun allDayEvent(row: CalendarInstanceRow, date: LocalDate, zone: ZoneId): CalendarEvent? {
        val firstDay = utcDate(row.beginMillis)
        val endDay = utcDate(row.endMillis) // exclusive
        if (date < firstDay || date >= endDay) return null
        return row.toEvent(
            start = firstDay.atStartOfDay(zone).toInstant(),
            end = endDay.atStartOfDay(zone).toInstant(),
            zone = zone,
        )
    }

    private fun timedEvent(row: CalendarInstanceRow, dayStart: Instant, dayEnd: Instant, zone: ZoneId): CalendarEvent? {
        val start = Instant.ofEpochMilli(row.beginMillis)
        val end = Instant.ofEpochMilli(row.endMillis)
        // Overlap: it starts before the day ends and ends after the day starts.
        // A zero-length event (a reminder at 9:00) counts if it starts within the day.
        val overlaps = start < dayEnd && (end > dayStart || (end == start && start >= dayStart))
        return if (overlaps) row.toEvent(start, end, zone) else null
    }

    private fun CalendarInstanceRow.toEvent(start: Instant, end: Instant, zone: ZoneId) = CalendarEvent(
        title = title.orEmpty().trim(),
        start = start.atZone(zone),
        end = end.atZone(zone),
        isAllDay = isAllDay,
        location = location?.trim()?.ifEmpty { null },
        calendarName = calendarName,
        calendarId = calendarId,
    )

    /**
     * Turns calendar rows into [PhoneCalendar]s. A calendar without a name is called after its account, and calendars
     * whose events do not reach the phone (hidden or not synced) are kept but marked, so the user can be told why.
     */
    fun calendars(rows: List<CalendarRow>): List<PhoneCalendar> = rows.mapNotNull { row ->
        val name = row.displayName?.trim()?.ifEmpty { null } ?: row.accountName?.trim()?.ifEmpty { null }
        name?.let {
            PhoneCalendar(
                id = row.id,
                name = it,
                accountName = row.accountName,
                color = row.color,
                isShown = row.isVisible && row.isSynced,
            )
        }
    }

    private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
