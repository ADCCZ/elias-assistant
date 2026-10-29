package io.github.adccz.elias.domain.calendar

import io.github.adccz.elias.domain.model.SourceResult
import java.time.Instant
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

    /** Switches reading all [calendarIds] on or off at once (one calendar, or a whole account). */
    suspend fun setRead(calendarIds: Set<Long>, read: Boolean)
}

/**
 * The phone's calendars as Elias should see them. Every part of the app reads events through this, so the rules hold
 * everywhere: in the briefings, on Plány and Data, and in what goes to the AI.
 *
 * - A switched-off calendar is left out. [calendars] still lists every calendar, so it can be switched on again.
 * - An event that is in several calendars (the same title and the same time, as with two family calendars) is kept
 *   only once. The copy in a topic calendar wins, so a plan stays with its topic.
 */
class SelectedCalendars(private val source: CalendarRepository, private val settings: CalendarSettingsRepository) :
    CalendarRepository {
    override suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>> {
        val result = source.eventsOn(date)
        if (result !is SourceResult.Success) return result
        val choice = choice()
        return result.copy(data = choice.apply(result.data))
    }

    override suspend fun eventsFrom(firstDay: LocalDate, days: Int): SourceResult<Map<LocalDate, List<CalendarEvent>>> {
        val result = source.eventsFrom(firstDay, days)
        if (result !is SourceResult.Success) return result
        val choice = choice()
        return result.copy(data = result.data.mapValues { (_, events) -> choice.apply(events) })
    }

    override suspend fun calendars(): SourceResult<List<PhoneCalendar>> = source.calendars()

    private class Choice(val hidden: Set<Long>, val topics: Set<Long>) {
        fun apply(events: List<CalendarEvent>): List<CalendarEvent> {
            val kept = LinkedHashMap<EventKey, CalendarEvent>()
            for (event in events) {
                if (event.calendarId != null && event.calendarId in hidden) continue
                val key = EventKey.of(event)
                val earlier = kept[key]
                // The first copy keeps its place in the list; a copy from a topic calendar takes it over.
                if (earlier == null || (!earlier.isInTopic() && event.isInTopic())) kept[key] = event
            }
            return kept.values.toList()
        }

        private fun CalendarEvent.isInTopic() = calendarId != null && calendarId in topics
    }

    /** What makes two events the same: the title without case and extra spaces, and the exact time. */
    private data class EventKey(val title: String, val start: Instant, val end: Instant, val isAllDay: Boolean) {
        companion object {
            private val spaces = Regex("""\s+""")

            fun of(event: CalendarEvent) = EventKey(
                title = event.title.trim().replace(spaces, " ").lowercase(),
                start = event.start.toInstant(),
                end = event.end.toInstant(),
                isAllDay = event.isAllDay,
            )
        }
    }

    // A broken settings file must not hide the whole calendar: then everything is read.
    private suspend fun choice(): Choice = try {
        Choice(hidden = settings.hiddenCalendarIds.first(), topics = settings.topicCalendarIds.first())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Choice(hidden = emptySet(), topics = emptySet())
    }
}
