package io.github.adccz.elias.domain.topics

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class TopicsTest {
    private val day = LocalDate.of(2026, 11, 3)
    private val school = Topic(2, "Škola")
    private val spanish = Topic(4, "Španělština")
    private val growth = Topic(3, "Seberozvoj")

    private fun event(title: String, calendarId: Long?, hour: Int = 9) = CalendarEvent(
        title = title,
        start = day.atTime(hour, 0).atZone(PRAGUE_ZONE),
        end = day.atTime(hour + 1, 0).atZone(PRAGUE_ZONE),
        isAllDay = false,
        location = null,
        calendarName = null,
        calendarId = calendarId,
    )

    private fun calendar(id: Long, name: String) = PhoneCalendar(id, name, "olda@example.com", null, isShown = true)

    @Test
    fun `orders topics alphabetically in Czech`() {
        val calendars = listOf(calendar(4, "Španělština"), calendar(2, "Škola"), calendar(3, "Seberozvoj"))

        assertEquals(listOf(growth, school, spanish), Topics.resolve(calendars, setOf(2, 3, 4)))
    }

    @Test
    fun `skips calendars that are not topics and ids whose calendar is gone`() {
        val calendars = listOf(calendar(1, "Osobní"), calendar(2, "Škola"))

        assertEquals(listOf(school), Topics.resolve(calendars, setOf(2, 42)))
    }

    @Test
    fun `splits a day into ordinary events and plans by topic`() {
        val dentist = event("Zubař", calendarId = 1, hour = 8)
        val essay = event("Připravit referát", calendarId = 2, hour = 10)
        val reading = event("Přečíst kapitolu", calendarId = 2, hour = 14)
        val lesson = event("Lekce pět", calendarId = 4, hour = 18)
        val unknownCalendar = event("Bez kalendáře", calendarId = null)

        val agenda = Topics.agenda(
            day,
            listOf(dentist, essay, reading, lesson, unknownCalendar),
            listOf(school, spanish),
        )

        assertEquals(listOf(dentist, unknownCalendar), agenda.events)
        assertEquals(
            listOf(TopicPlans(school, listOf(essay, reading)), TopicPlans(spanish, listOf(lesson))),
            agenda.topics,
        )
    }

    @Test
    fun `lists the topics with nothing planned`() {
        val agenda = Topics.agenda(day, listOf(event("Lekce", calendarId = 4)), listOf(growth, school, spanish))

        assertEquals(listOf(spanish), agenda.topicsWithPlans.map { it.topic })
        assertEquals(listOf(growth, school), agenda.topicsWithoutPlans)
    }

    @Test
    fun `without topics every event is ordinary`() {
        val events = listOf(event("Přednáška", calendarId = 2))

        val agenda = Topics.agenda(day, events, topics = emptyList())

        assertEquals(events, agenda.events)
        assertEquals(emptyList<TopicPlans>(), agenda.topics)
    }

    @Test
    fun `the default range read asks day by day and stops at the first failure`() = runTest {
        val loadedAt = Instant.parse("2026-11-03T06:00:00Z")
        val asked = mutableListOf<LocalDate>()
        val repository = object : CalendarRepository {
            override suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>> {
                asked += date
                return if (date == day.plusDays(2)) {
                    SourceResult.Failure(FailureReason.NO_PERMISSION)
                } else {
                    SourceResult.Success(listOf(event("Den ${date.dayOfMonth}", 1)), loadedAt)
                }
            }
        }

        val twoDays = repository.eventsFrom(day, days = 2) as SourceResult.Success
        assertEquals(listOf(day, day.plusDays(1)), twoDays.data.keys.toList())
        assertEquals("Den 4", twoDays.data.getValue(day.plusDays(1)).single().title)

        asked.clear()
        assertInstanceOf(SourceResult.Failure::class.java, repository.eventsFrom(day, days = 5))
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), asked)
    }
}
