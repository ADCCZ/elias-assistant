package io.github.adccz.elias.domain.schedule

import io.github.adccz.elias.domain.sun.SunCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** What Elias does on its own at a set time. */
enum class ScheduledTask {
    /** Weather and headlines are loaded and cached before the morning briefing, so the morning needs no network. */
    PREFETCH,

    /** The evening briefing plays on its own. */
    EVENING_BRIEFING,

    /** A notification an hour before the sabbath begins. */
    SABBATH_REMINDER,
}

/**
 * One planned run of [task].
 *
 * [afterSabbath] marks Saturday's evening briefing, which opens with "the sabbath has ended".
 */
data class ScheduledRun(val task: ScheduledTask, val at: ZonedDateTime, val afterSabbath: Boolean = false)

/**
 * Works out when each task runs next, from the weekly schedule and the sabbath rules of the spec:
 *
 * - The sabbath lasts from sunset on Friday to sunset on Saturday.
 * - No evening briefing on Friday, no morning briefing (so no prefetch) on Saturday.
 * - Saturday's evening briefing never plays before sunset, and it opens with "the sabbath has ended".
 * - A reminder an hour before Friday's sunset.
 *
 * Each run is computed from its calendar date and clock time in the Prague zone, never by adding 24 hours,
 * so runs keep their clock time across daylight saving changes. Pure logic, unit-tested on the JVM.
 */
class ScheduleCalculator @Inject constructor(private val sun: SunCalculator, private val zone: ZoneId) {
    /** The next run of [task] strictly after [now], or null when the schedule has it switched off on every day. */
    fun next(task: ScheduledTask, schedule: WeeklySchedule, now: ZonedDateTime): ScheduledRun? {
        val today = now.withZoneSameInstant(zone).toLocalDate()
        // From yesterday: the prefetch for a morning just after midnight falls on the evening before.
        return (-1L..DAYS_AHEAD).asSequence()
            .mapNotNull { offset -> candidate(task, schedule, today.plusDays(offset)) }
            .filter { it.at.isAfter(now) }
            .minByOrNull { it.at.toInstant() }
    }

    /** The next run of every task that is switched on, ordered by time. */
    fun nextRuns(schedule: WeeklySchedule, now: ZonedDateTime): List<ScheduledRun> =
        ScheduledTask.entries.mapNotNull { next(it, schedule, now) }.sortedBy { it.at.toInstant() }

    private fun candidate(task: ScheduledTask, schedule: WeeklySchedule, date: LocalDate): ScheduledRun? =
        when (task) {
            ScheduledTask.PREFETCH -> prefetchFor(date, schedule)
            ScheduledTask.EVENING_BRIEFING -> eveningOn(date, schedule)
            ScheduledTask.SABBATH_REMINDER -> reminderOn(date)
        }

    private fun prefetchFor(morningDate: LocalDate, schedule: WeeklySchedule): ScheduledRun? {
        // No morning briefing on the sabbath, so there is nothing to prepare.
        if (morningDate.dayOfWeek == DayOfWeek.SATURDAY) return null
        val morning = schedule.on(morningDate.dayOfWeek).morning ?: return null
        // A time in the skipped hour of spring moves forward by an hour, a time in the repeated hour of autumn
        // means its first occurrence (ZonedDateTime.of does both).
        val briefingAt = ZonedDateTime.of(morningDate, morning, zone)
        return ScheduledRun(ScheduledTask.PREFETCH, briefingAt.minus(PREFETCH_LEAD))
    }

    private fun eveningOn(date: LocalDate, schedule: WeeklySchedule): ScheduledRun? {
        val evening = schedule.on(date.dayOfWeek).evening ?: return null
        val at = ZonedDateTime.of(date, evening, zone)
        return when (date.dayOfWeek) {
            // The sabbath begins at sunset on Friday.
            DayOfWeek.FRIDAY -> null

            DayOfWeek.SATURDAY -> {
                val sabbathEnd = sunset(date)
                val afterSunset = if (at.isBefore(sabbathEnd)) sabbathEnd else at
                ScheduledRun(ScheduledTask.EVENING_BRIEFING, afterSunset, afterSabbath = true)
            }

            else -> ScheduledRun(ScheduledTask.EVENING_BRIEFING, at)
        }
    }

    private fun reminderOn(date: LocalDate): ScheduledRun? = if (date.dayOfWeek == DayOfWeek.FRIDAY) {
        ScheduledRun(ScheduledTask.SABBATH_REMINDER, sunset(date).minus(REMINDER_LEAD))
    } else {
        null
    }

    // The sun sets every day at Elias's latitude, so a missing sunset would be a programming error.
    private fun sunset(date: LocalDate): ZonedDateTime =
        checkNotNull(sun.sunTimes(date).sunset) { "No sunset on $date at this location" }

    companion object {
        /** The spec: data is prefetched 30 minutes before the morning briefing. */
        val PREFETCH_LEAD: Duration = Duration.ofMinutes(30)

        /** The spec: the sabbath reminder comes an hour before Friday's sunset. */
        val REMINDER_LEAD: Duration = Duration.ofHours(1)

        /** A week and a day: enough to find the next run of any day of the week, starting from yesterday. */
        private const val DAYS_AHEAD = 8L
    }
}
