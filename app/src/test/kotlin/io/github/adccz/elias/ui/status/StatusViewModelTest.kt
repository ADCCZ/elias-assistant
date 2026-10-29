package io.github.adccz.elias.ui.status

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.schedule.DayTimes
import io.github.adccz.elias.domain.schedule.ScheduleCalculator
import io.github.adccz.elias.domain.schedule.ScheduleRepository
import io.github.adccz.elias.domain.schedule.ScheduledTask
import io.github.adccz.elias.domain.schedule.WeeklySchedule
import io.github.adccz.elias.domain.status.SystemChecks
import io.github.adccz.elias.domain.status.SystemStatus
import io.github.adccz.elias.domain.sun.SunCalculator
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.testing.FakeRunLog
import io.github.adccz.elias.testing.FakeVoiceChecker
import io.github.adccz.elias.testing.MainDispatcherExtension
import io.github.adccz.elias.testing.testEngine
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class StatusViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-10-28T19:00:00Z"), PRAGUE_ZONE)
    private var system =
        SystemStatus(
            calendar = true,
            notifications = false,
            exactAlarms = true,
            batteryUnrestricted = false,
            fullScreen = true,
        )
    private val voiceChecker = FakeVoiceChecker()
    private val runLog = FakeRunLog()
    private val schedules = object : ScheduleRepository {
        override val schedule = MutableStateFlow(WeeklySchedule.DEFAULT)

        override suspend fun setDay(day: DayOfWeek, times: DayTimes) = Unit
    }

    private fun viewModel() = StatusViewModel(
        checks = SystemChecks { system },
        voiceChecker = voiceChecker,
        schedules = schedules,
        calculator = ScheduleCalculator(SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE), PRAGUE_ZONE),
        runLog = runLog,
        clock = clock,
    )

    @Test
    fun `shows what the phone allows, the voice and the plan`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(system, state.system)
        assertEquals(VoiceStatus.Ready(testEngine, "cs-local"), state.voice)
        assertEquals(ScheduledTask.entries.toSet(), state.nextRuns.map { it.task }.toSet())
        assertEquals(null, state.lastRun)
    }

    @Test
    fun `checks again after a fix in the settings`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        system = system.copy(notifications = true, batteryUnrestricted = true)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(system, viewModel.uiState.value.system)
        assertEquals(2, voiceChecker.checks)
    }

    @Test
    fun `shows the latest run`() = runTest {
        val viewModel = viewModel()
        val run =
            RunRecord(
                RunKind.EVENING_BRIEFING,
                RunTrigger.SCHEDULE,
                clock.instant(),
                clock.instant(),
                RunOutcome.PLAYED,
            )

        runLog.record(run)
        advanceUntilIdle()

        assertEquals(run, viewModel.uiState.value.lastRun)
    }
}
