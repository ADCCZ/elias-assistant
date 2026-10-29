package io.github.adccz.elias.domain.schedule

import java.time.DayOfWeek
import java.time.LocalTime

/** The two times Elias works on one day of the week. Null means "off" on that day. */
data class DayTimes(val morning: LocalTime?, val evening: LocalTime?)

/**
 * When Elias works, set separately for each day of the week. Both times are clock times on that calendar day.
 *
 * - The morning time is when the morning briefing is due: data is prefetched before it, and from M6 the alarm rings.
 * - The evening time is when the evening briefing plays on its own.
 *
 * The sabbath rules apply on top of this (see [ScheduleCalculator]), whatever is set for Friday and Saturday.
 */
data class WeeklySchedule(val days: Map<DayOfWeek, DayTimes>) {
    init {
        require(days.keys == DayOfWeek.entries.toSet()) { "A schedule needs all seven days, got ${days.keys}" }
    }

    fun on(day: DayOfWeek): DayTimes = days.getValue(day)

    fun with(day: DayOfWeek, times: DayTimes): WeeklySchedule = WeeklySchedule(days + (day to times))

    companion object {
        val DEFAULT_MORNING: LocalTime = LocalTime.of(7, 0)
        val DEFAULT_EVENING: LocalTime = LocalTime.of(22, 0)

        /** The user's choice from the M4 plan: the morning briefing at seven, the evening one at ten, every day. */
        val DEFAULT = WeeklySchedule(DayOfWeek.entries.associateWith { DayTimes(DEFAULT_MORNING, DEFAULT_EVENING) })
    }
}
