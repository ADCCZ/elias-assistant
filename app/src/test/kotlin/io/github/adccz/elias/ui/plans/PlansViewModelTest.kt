package io.github.adccz.elias.ui.plans

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import io.github.adccz.elias.domain.calendar.SelectedCalendars
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.topics.Topic
import io.github.adccz.elias.testing.FakeCalendarSettings
import io.github.adccz.elias.testing.MainDispatcherExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    // Thursday 29 October 2026, 20:00 in Prague.
    private val clock = Clock.fixed(
        LocalDateTime.parse("2026-10-29T20:00").atZone(PRAGUE_ZONE).toInstant(),
        PRAGUE_ZONE,
    )
    private val today = LocalDate.of(2026, 10, 29)

    private class FakeCalendar(val today: LocalDate, var permissionGranted: Boolean = true) : CalendarRepository {
        val calendars = listOf(
            PhoneCalendar(1, "Osobní", "olda@example.com", null, isShown = true),
            PhoneCalendar(2, "Škola", "olda@example.com", 0xFF3366CC.toInt(), isShown = true),
            PhoneCalendar(3, "Seberozvoj", "olda@example.com", 0xFF33AA66.toInt(), isShown = true),
        )

        private fun event(title: String, calendarId: Long, date: LocalDate) = CalendarEvent(
            title = title,
            start = date.atTime(10, 0).atZone(PRAGUE_ZONE),
            end = date.atTime(11, 0).atZone(PRAGUE_ZONE),
            isAllDay = false,
            location = null,
            calendarName = null,
            calendarId = calendarId,
        )

        override suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>> {
            if (!permissionGranted) return SourceResult.Failure(FailureReason.NO_PERMISSION)
            val events = when (date) {
                today -> listOf(event("Zubař", 1, date), event("Připravit referát", 2, date))
                today.plusDays(1) -> listOf(event("Číst dvacet minut", 3, date))
                else -> emptyList()
            }
            return SourceResult.Success(events, Instant.EPOCH)
        }

        override suspend fun calendars(): SourceResult<List<PhoneCalendar>> = if (permissionGranted) {
            SourceResult.Success(calendars, Instant.EPOCH)
        } else {
            SourceResult.Failure(FailureReason.NO_PERMISSION)
        }
    }

    private val calendar = FakeCalendar(today)
    private val calendarSettings = FakeCalendarSettings(setOf(2L))

    // Created inside each test, after MainDispatcherExtension has set the main dispatcher.
    // Through SelectedCalendars, as in the app, so switching a calendar off really hides its events.
    private val viewModel by lazy {
        PlansViewModel(SelectedCalendars(calendar, calendarSettings), calendarSettings, clock)
    }

    private fun TestScope.collectState() =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    @Test
    fun `shows a week from today split into events and plans by topic`() = runTest {
        collectState()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(PlansViewModel.DAYS, state.days.size)
        assertEquals(today, state.days.first().date)
        assertEquals(listOf("Zubař"), state.days.first().events.map { it.title })
        val school = state.days.first().topics.single()
        assertEquals(Topic(2, "Škola"), school.topic)
        assertEquals(listOf("Připravit referát"), school.plans.map { it.title })
        assertEquals(mapOf(2L to 0xFF3366CC.toInt()), state.topicColors)
        assertTrue(state.hasTopics)
    }

    @Test
    fun `marking a calendar as a topic regroups the days at once`() = runTest {
        collectState()
        advanceUntilIdle()

        viewModel.setTopic(3, isTopic = true)
        advanceUntilIdle()

        val tomorrow = viewModel.uiState.value.days[1]
        assertEquals(listOf("Seberozvoj", "Škola"), tomorrow.topics.map { it.topic.name })
        assertEquals(listOf("Číst dvacet minut"), tomorrow.topicsWithPlans.single().plans.map { it.title })
        assertEquals(listOf(Topic(2, "Škola")), tomorrow.topicsWithoutPlans)
        assertEquals(listOf(false, true, true), viewModel.uiState.value.calendars.map { it.isTopic })
    }

    @Test
    fun `asks for the permission and reads the calendar once it is granted`() = runTest {
        calendar.permissionGranted = false
        collectState()
        advanceUntilIdle()
        assertEquals(FailureReason.NO_PERMISSION, viewModel.uiState.value.problem)

        calendar.permissionGranted = true
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.problem)
        assertEquals(PlansViewModel.DAYS, viewModel.uiState.value.days.size)
    }

    @Test
    fun `a calendar switched off disappears from the days and can no longer be a topic`() = runTest {
        collectState()
        advanceUntilIdle()

        viewModel.setRead(2, read = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val today = state.days.first()
        assertEquals(listOf("Zubař"), today.events.map { it.title })
        assertEquals(emptyList<Topic>(), today.topics.map { it.topic })
        assertFalse(state.hasTopics)
        assertEquals(listOf(true, false, true), state.calendars.map { it.isRead })

        viewModel.setRead(2, read = true)
        advanceUntilIdle()
        assertEquals(listOf("Připravit referát"), viewModel.uiState.value.days.first().topics.single().plans.map { it.title })
    }
}
