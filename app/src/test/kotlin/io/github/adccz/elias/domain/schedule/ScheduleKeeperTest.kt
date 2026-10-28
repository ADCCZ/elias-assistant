package io.github.adccz.elias.domain.schedule

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.sun.SunCalculator
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduleKeeperTest {
    // Thursday 29 October 2026, 23:00 in Prague.
    private val clock = Clock.fixed(
        LocalDateTime.parse("2026-10-29T23:00").atZone(PRAGUE_ZONE).toInstant(),
        PRAGUE_ZONE,
    )
    private val calculator = ScheduleCalculator(SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE), PRAGUE_ZONE)
    private val schedules = FakeScheduleRepository()
    private val alarms = FakeAlarmScheduler()
    private val keeper = ScheduleKeeper(schedules, calculator, alarms, clock)

    @Test
    fun `sets one alarm per task for its next run`() = runTest {
        val runs = keeper.reschedule()

        assertEquals(ScheduledTask.entries.toSet(), alarms.set.keys)
        assertEquals(runs.associateBy { it.task }, alarms.set)
        assertEquals(
            LocalDateTime.parse("2026-10-30T06:30"),
            alarms.set.getValue(ScheduledTask.PREFETCH).at.toLocalDateTime(),
        )
    }

    @Test
    fun `cancels the alarm of a task that is switched off everywhere`() = runTest {
        schedules.schedule.value = DayOfWeek.entries.fold(WeeklySchedule.DEFAULT) { schedule, day ->
            schedule.with(day, schedule.on(day).copy(evening = null))
        }

        keeper.reschedule()

        assertEquals(setOf(ScheduledTask.EVENING_BRIEFING), alarms.cancelled)
        assertFalse(ScheduledTask.EVENING_BRIEFING in alarms.set)
    }

    @Test
    fun `a briefing that fires a little late still plays`() {
        val planned = Instant.parse("2026-10-29T21:00:00Z")

        assertTrue(ScheduleKeeper.isStillDue(ScheduledTask.EVENING_BRIEFING, planned, planned.plusSeconds(14 * 60)))
    }

    @Test
    fun `a briefing that fires much later than planned does not play`() {
        val planned = Instant.parse("2026-10-29T21:00:00Z")

        assertFalse(ScheduleKeeper.isStillDue(ScheduledTask.EVENING_BRIEFING, planned, planned.plusSeconds(16 * 60)))
        assertFalse(ScheduleKeeper.isStillDue(ScheduledTask.SABBATH_REMINDER, planned, planned.plusSeconds(3600)))
    }

    @Test
    fun `a late prefetch is still worth it`() {
        val planned = Instant.parse("2026-10-29T05:30:00Z")

        assertTrue(ScheduleKeeper.isStillDue(ScheduledTask.PREFETCH, planned, planned.plusSeconds(2 * 3600)))
    }

    private class FakeScheduleRepository : ScheduleRepository {
        override val schedule = MutableStateFlow(WeeklySchedule.DEFAULT)

        override suspend fun setDay(day: DayOfWeek, times: DayTimes) {
            schedule.value = schedule.value.with(day, times)
        }
    }

    private class FakeAlarmScheduler : AlarmScheduler {
        val set = mutableMapOf<ScheduledTask, ScheduledRun>()
        val cancelled = mutableSetOf<ScheduledTask>()

        override fun set(run: ScheduledRun) {
            set[run.task] = run
        }

        override fun cancel(task: ScheduledTask) {
            cancelled += task
            set.remove(task)
        }
    }
}
