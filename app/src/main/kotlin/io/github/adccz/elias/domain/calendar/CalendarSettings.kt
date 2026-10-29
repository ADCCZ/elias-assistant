package io.github.adccz.elias.domain.calendar

import io.github.adccz.elias.domain.model.SourceResult
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** What the user chose for the phone's calendars on Plány. Stored on the phone. */
interface CalendarSettingsRepository {
    /** Ids of the calendars marked as topics (M4.5). Emits again on every change. */
    val topicCalendarIds: Flow<Set<Long>>

    /** Ids of the calendars Elias must not read at all. Every other calendar is read, also a new one. */
    val hiddenCalendarIds: Flow<Set<Long>>

    suspend fun setTopic(calendarId: Long, isTopic: Boolean)

    suspend fun setRead(calendarId: Long, read: Boolean)
}

/**
 * The phone's calendars without the ones the user switched off. Every part of the app reads events through this,
 * so a switched-off calendar is left out everywhere: in the briefings, on Plány and Data, and in what goes to the AI.
 * [calendars] still lists every calendar, so it can be switched on again.
 */
class SelectedCalendars(private val source: CalendarRepository, private val settings: CalendarSettingsRepository) :
    CalendarRepository {
    override suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>> {
        val result = source.eventsOn(date)
        if (result !is SourceResult.Success) return result
        val hidden = hiddenIds()
        return result.copy(data = result.data.withoutHidden(hidden))
    }

    override suspend fun eventsFrom(firstDay: LocalDate, days: Int): SourceResult<Map<LocalDate, List<CalendarEvent>>> {
        val result = source.eventsFrom(firstDay, days)
        if (result !is SourceResult.Success) return result
        val hidden = hiddenIds()
        return result.copy(data = result.data.mapValues { (_, events) -> events.withoutHidden(hidden) })
    }

    override suspend fun calendars(): SourceResult<List<PhoneCalendar>> = source.calendars()

    // A broken settings file must not hide the whole calendar: then everything is read.
    private suspend fun hiddenIds(): Set<Long> = try {
        settings.hiddenCalendarIds.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptySet()
    }

    private fun List<CalendarEvent>.withoutHidden(hidden: Set<Long>) =
        if (hidden.isEmpty()) this else filterNot { it.calendarId != null && it.calendarId in hidden }
}
