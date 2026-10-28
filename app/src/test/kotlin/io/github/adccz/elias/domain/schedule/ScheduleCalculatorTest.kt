package io.github.adccz.elias.domain.schedule

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.schedule.ScheduledTask.EVENING_BRIEFING
import io.github.adccz.elias.domain.schedule.ScheduledTask.PREFETCH
import io.github.adccz.elias.domain.schedule.ScheduledTask.SABBATH_REMINDER
import io.github.adccz.elias.domain.sun.SunCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduleCalculatorTest {
    private val sun = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)
    private val calculator = ScheduleCalculator(sun, PRAGUE_ZONE)
    private val schedule = WeeklySchedule.DEFAULT

    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(PRAGUE_ZONE)

    private fun sunset(date: String): ZonedDateTime = sun.sunTimes(LocalDate.parse(date)).sunset!!

    private fun WeeklySchedule.withEvening(day: DayOfWeek, time: LocalTime?) = with(day, on(day).copy(evening = time))

    private fun WeeklySchedule.withMorning(day: DayOfWeek, time: LocalTime?) = with(day, on(day).copy(morning = time))

    // 2026-10-26 is a Monday, 2026-10-30 a Friday, 2026-10-31 a Saturday.

    @Test
    fun `plays the evening briefing at the set time today`() {
        val run = calculator.next(EVENING_BRIEFING, schedule, at("2026-10-26T12:00"))

        assertEquals(ScheduledRun(EVENING_BRIEFING, at("2026-10-26T22:00")), run)
    }

    @Test
    fun `once the evening time has come the next briefing is tomorrow`() {
        val run = calculator.next(EVENING_BRIEFING, schedule, at("2026-10-26T22:00"))

        assertEquals(at("2026-10-27T22:00"), run?.at)
    }

    @Test
    fun `uses the time set for each day`() {
        val custom = schedule.withEvening(DayOfWeek.TUESDAY, LocalTime.of(20, 15))

        val run = calculator.next(EVENING_BRIEFING, custom, at("2026-10-26T23:00"))

        assertEquals(at("2026-10-27T20:15"), run?.at)
    }

    @Test
    fun `skips days with the evening switched off`() {
        val custom = schedule.withEvening(DayOfWeek.TUESDAY, null)

        val run = calculator.next(EVENING_BRIEFING, custom, at("2026-10-27T12:00"))

        assertEquals(at("2026-10-28T22:00"), run?.at)
    }

    @Test
    fun `plans nothing when every evening is switched off`() {
        val allOff = DayOfWeek.entries.fold(schedule) { result, day -> result.withEvening(day, null) }

        assertNull(calculator.next(EVENING_BRIEFING, allOff, at("2026-10-26T12:00")))
    }

    @Test
    fun `skips the evening briefing on Friday`() {
        val run = calculator.next(EVENING_BRIEFING, schedule, at("2026-10-30T12:00"))

        assertEquals(ScheduledRun(EVENING_BRIEFING, at("2026-10-31T22:00"), afterSabbath = true), run)
    }

    @Test
    fun `only Saturday's evening briefing opens with the end of the sabbath`() {
        val sunday = calculator.next(EVENING_BRIEFING, schedule, at("2026-10-31T23:00"))

        assertEquals(at("2026-11-01T22:00"), sunday?.at)
        assertFalse(sunday!!.afterSabbath)
    }

    @Test
    fun `on Saturday the evening briefing waits for sunset`() {
        val early = schedule.withEvening(DayOfWeek.SATURDAY, LocalTime.of(15, 0))

        val run = calculator.next(EVENING_BRIEFING, early, at("2026-10-31T09:00"))

        assertEquals(sunset("2026-10-31"), run?.at)
        assertTrue(run!!.afterSabbath)
    }

    @Test
    fun `in summer Saturday's evening briefing waits for the late sunset`() {
        // Sunset in Pilsen is after nine in the evening in late June.
        val run = calculator.next(
            EVENING_BRIEFING,
            schedule.withEvening(DayOfWeek.SATURDAY, LocalTime.of(20, 0)),
            at("2027-06-26T12:00"),
        )

        assertEquals(sunset("2027-06-26"), run?.at)
        assertTrue(run!!.at.toLocalTime().isAfter(LocalTime.of(21, 0)))
    }

    @Test
    fun `prefetches thirty minutes before the morning briefing`() {
        val run = calculator.next(PREFETCH, schedule, at("2026-10-26T05:00"))

        assertEquals(ScheduledRun(PREFETCH, at("2026-10-26T06:30")), run)
    }

    @Test
    fun `does not prefetch for Saturday morning`() {
        val run = calculator.next(PREFETCH, schedule, at("2026-10-30T12:00"))

        assertEquals(at("2026-11-01T06:30"), run?.at)
    }

    @Test
    fun `skips mornings that are switched off`() {
        val custom = schedule.withMorning(DayOfWeek.TUESDAY, null)

        val run = calculator.next(PREFETCH, custom, at("2026-10-26T12:00"))

        assertEquals(at("2026-10-28T06:30"), run?.at)
    }

    @Test
    fun `the prefetch for a morning just after midnight falls on the evening before`() {
        val custom = schedule.withMorning(DayOfWeek.TUESDAY, LocalTime.of(0, 15))

        val run = calculator.next(PREFETCH, custom, at("2026-10-26T20:00"))

        assertEquals(at("2026-10-26T23:45"), run?.at)
    }

    @Test
    fun `reminds of the sabbath an hour before Friday's sunset`() {
        val run = calculator.next(SABBATH_REMINDER, schedule, at("2026-10-26T12:00"))

        assertEquals(ScheduledRun(SABBATH_REMINDER, sunset("2026-10-30").minusHours(1)), run)
    }

    @Test
    fun `after Friday's reminder the next one is a week later`() {
        val afterReminder = sunset("2026-10-30").minusMinutes(30)

        val run = calculator.next(SABBATH_REMINDER, schedule, afterReminder)

        assertEquals(sunset("2026-11-06").minusHours(1), run?.at)
    }

    @Test
    fun `the evening briefing keeps its clock time when summer time ends`() {
        // 25 October 2026: clocks go back from 3:00 to 2:00, so Saturday 22:00 to Sunday 22:00 is 25 hours.
        val saturday = at("2026-10-24T22:00")

        val sunday = calculator.next(EVENING_BRIEFING, schedule, saturday)!!.at

        assertEquals(LocalDateTime.parse("2026-10-25T22:00"), sunday.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(1), sunday.offset)
        assertEquals(Duration.ofHours(25), Duration.between(saturday, sunday))
    }

    @Test
    fun `the prefetch keeps its clock time when summer time starts`() {
        // 28 March 2027: clocks go forward from 2:00 to 3:00.
        val run = calculator.next(PREFETCH, schedule, at("2027-03-27T23:00"))!!.at

        assertEquals(LocalDateTime.parse("2027-03-28T06:30"), run.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(2), run.offset)
    }

    @Test
    fun `a morning in the skipped spring hour moves an hour forward`() {
        val custom = schedule.withMorning(DayOfWeek.SUNDAY, LocalTime.of(2, 30))

        val run = calculator.next(PREFETCH, custom, at("2027-03-27T23:00"))!!.at

        // 2:30 does not exist that night, so the morning is 3:30 summer time and the prefetch 3:00.
        assertEquals(ZonedDateTime.parse("2027-03-28T03:00+02:00[Europe/Prague]"), run)
    }

    @Test
    fun `a morning in the repeated autumn hour uses its first occurrence`() {
        val custom = schedule.withMorning(DayOfWeek.SUNDAY, LocalTime.of(2, 30))

        val run = calculator.next(PREFETCH, custom, at("2026-10-24T23:00"))!!.at

        assertEquals(ZonedDateTime.parse("2026-10-25T02:00+02:00[Europe/Prague]"), run)
    }

    @Test
    fun `lists the next run of every task in time order`() {
        val runs = calculator.nextRuns(schedule, at("2026-10-29T23:00")) // Thursday night

        assertEquals(listOf(PREFETCH, SABBATH_REMINDER, EVENING_BRIEFING), runs.map { it.task })
        assertEquals(at("2026-10-30T06:30"), runs[0].at)
        assertEquals(at("2026-10-31T22:00"), runs[2].at)
    }

    @Test
    fun `a schedule must name all seven days`() {
        val result = runCatching { WeeklySchedule(mapOf(DayOfWeek.MONDAY to DayTimes(null, null))) }

        assertTrue(result.isFailure)
    }
}
