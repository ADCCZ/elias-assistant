package io.github.adccz.elias.ui.schedule

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.schedule.DayTimes
import io.github.adccz.elias.domain.schedule.ScheduleCalculator
import io.github.adccz.elias.domain.schedule.ScheduleRepository
import io.github.adccz.elias.domain.schedule.ScheduledTask
import io.github.adccz.elias.domain.schedule.WeeklySchedule
import io.github.adccz.elias.domain.sun.SunCalculator
import io.github.adccz.elias.testing.MainDispatcherExtension
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    // Wednesday 28 October 2026, 20:00 in Prague.
    private val clock = Clock.fixed(
        LocalDateTime.parse("2026-10-28T20:00").atZone(PRAGUE_ZONE).toInstant(),
        PRAGUE_ZONE,
    )
    private val repository = object : ScheduleRepository {
        override val schedule = MutableStateFlow(WeeklySchedule.DEFAULT)

        override suspend fun setDay(day: DayOfWeek, times: DayTimes) {
            schedule.value = schedule.value.with(day, times)
        }
    }

    // Created inside each test, after MainDispatcherExtension has set the main dispatcher.
    private val viewModel by lazy {
        ScheduleViewModel(
            repository,
            ScheduleCalculator(SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE), PRAGUE_ZONE),
            clock,
        )
    }

    @Test
    fun `shows the schedule and what is planned next`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WeeklySchedule.DEFAULT, state.schedule)
        val evening = state.nextRuns.first { it.task == ScheduledTask.EVENING_BRIEFING }
        assertEquals(LocalDateTime.parse("2026-10-28T22:00"), evening.at.toLocalDateTime())
    }

    @Test
    fun `changes one time of a day and keeps the other`() = runTest {
        viewModel.setTime(DayOfWeek.WEDNESDAY, TimeSlot.EVENING, LocalTime.of(21, 15))
        advanceUntilIdle()

        assertEquals(
            DayTimes(WeeklySchedule.DEFAULT_MORNING, LocalTime.of(21, 15)),
            repository.schedule.value.on(DayOfWeek.WEDNESDAY),
        )
    }

    @Test
    fun `switches a time off`() = runTest {
        viewModel.setTime(DayOfWeek.SUNDAY, TimeSlot.MORNING, null)
        advanceUntilIdle()

        assertEquals(null, repository.schedule.value.on(DayOfWeek.SUNDAY).morning)
        assertEquals(WeeklySchedule.DEFAULT_EVENING, repository.schedule.value.on(DayOfWeek.SUNDAY).evening)
    }

    @Test
    fun `a changed time moves what is planned next`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.setTime(DayOfWeek.WEDNESDAY, TimeSlot.EVENING, LocalTime.of(20, 30))
        advanceUntilIdle()

        val evening = viewModel.uiState.value.nextRuns.first { it.task == ScheduledTask.EVENING_BRIEFING }
        assertEquals(LocalDateTime.parse("2026-10-28T20:30"), evening.at.toLocalDateTime())
    }
}
