package io.github.adccz.elias.domain.schedule

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Sets and cancels the phone's alarms. Implemented with Android's AlarmManager. */
interface AlarmScheduler {
    /** Sets the alarm for [run]'s task at its time, replacing the task's previous alarm. */
    fun set(run: ScheduledRun)

    /** Removes the task's alarm, if there is one. */
    fun cancel(task: ScheduledTask)
}

/**
 * Keeps one alarm per task, always for its next run. Alarms are forgotten when the phone restarts, and they are
 * wrong after the time or the time zone changes, so this runs after each of those, after an update of the app,
 * whenever the schedule changes and after every alarm.
 */
class ScheduleKeeper @Inject constructor(
    private val schedules: ScheduleRepository,
    private val calculator: ScheduleCalculator,
    private val alarms: AlarmScheduler,
    private val clock: Clock,
) {
    /** Plans the next run of every task from now and returns them in time order. Safe to call any time. */
    suspend fun reschedule(): List<ScheduledRun> {
        val runs = calculator.nextRuns(schedules.schedule.first(), ZonedDateTime.now(clock))
        ScheduledTask.entries.forEach { task ->
            runs.firstOrNull { it.task == task }?.let(alarms::set) ?: alarms.cancel(task)
        }
        return runs
    }

    companion object {
        /**
         * How late an alarm may fire and still run. A briefing that comes much later than planned (the clock was
         * changed, the phone was off) would only surprise the user, so it waits for the next run instead.
         */
        val LATE_LIMIT: Duration = Duration.ofMinutes(15)

        /** Whether a run planned for [plannedAt] should still happen at [now]. A prefetch is always worth it. */
        fun isStillDue(task: ScheduledTask, plannedAt: Instant, now: Instant): Boolean =
            task == ScheduledTask.PREFETCH || !now.isAfter(plannedAt.plus(LATE_LIMIT))
    }
}
