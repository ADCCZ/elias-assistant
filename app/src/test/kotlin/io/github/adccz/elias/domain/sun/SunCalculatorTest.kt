package io.github.adccz.elias.domain.sun

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.model.GeoLocation
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SunCalculatorTest {
    private val calculator = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)

    /**
     * Reference values for Pilsen from the US Naval Observatory (aa.usno.navy.mil), rounded to the minute
     * and converted to Prague local time. DST started on 29 March 2026 and ended on 25 October 2026,
     * so local times jump by an hour on those days.
     */
    @ParameterizedTest(name = "{0}: sunrise {1}, sunset {2}")
    @CsvSource(
        "2026-03-28, 05:52, 18:32", // last day of winter time
        "2026-03-29, 06:50, 19:33", // first day of summer time
        "2026-06-21, 04:58, 21:18", // summer solstice
        "2026-10-24, 07:42, 17:58", // last day of summer time
        "2026-10-25, 06:44, 16:57", // first day of winter time
        "2026-10-30, 06:52, 16:48",
        "2025-12-21, 08:01, 16:08", // winter solstice
    )
    fun `matches USNO reference times within a minute`(date: LocalDate, sunrise: LocalTime, sunset: LocalTime) {
        val times = calculator.sunTimes(date)

        assertWithinOneMinute(date.atTime(sunrise).atZone(PRAGUE_ZONE), times.sunrise)
        assertWithinOneMinute(date.atTime(sunset).atZone(PRAGUE_ZONE), times.sunset)
    }

    @Test
    fun `returns null when the sun never sets`() {
        val northCape = SunCalculator(GeoLocation(latitude = 71.17, longitude = 25.78), PRAGUE_ZONE)

        val midsummer = northCape.sunTimes(LocalDate.of(2026, 6, 21))

        assertNull(midsummer.sunrise)
        assertNull(midsummer.sunset)
    }

    private fun assertWithinOneMinute(expected: ZonedDateTime, actual: ZonedDateTime?) {
        requireNotNull(actual)
        val difference = Duration.between(expected, actual).abs()
        assertTrue(difference <= Duration.ofSeconds(60)) { "Expected $expected but was $actual" }
    }
}
