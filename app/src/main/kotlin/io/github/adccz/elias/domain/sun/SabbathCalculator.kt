package io.github.adccz.elias.domain.sun

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** The sabbath: from sunset on Friday to sunset on Saturday. */
data class SabbathWindow(val start: ZonedDateTime, val end: ZonedDateTime) {
    operator fun contains(moment: ZonedDateTime): Boolean = !moment.isBefore(start) && moment.isBefore(end)
}

class SabbathCalculator @Inject constructor(private val sun: SunCalculator) {
    /** The sabbath in progress at [now], or the next one when none is in progress. */
    fun currentOrNext(now: ZonedDateTime): SabbathWindow {
        val friday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
        val window = windowStartingOn(friday)
        return if (now.isBefore(window.end)) window else windowStartingOn(friday.plusWeeks(1))
    }

    private fun windowStartingOn(friday: LocalDate): SabbathWindow = SabbathWindow(
        start = sunsetOn(friday),
        end = sunsetOn(friday.plusDays(1)),
    )

    // The sun sets every day at Elias's latitude, so a missing sunset would be a programming error.
    private fun sunsetOn(date: LocalDate): ZonedDateTime =
        checkNotNull(sun.sunTimes(date).sunset) { "No sunset on $date at this location" }
}
