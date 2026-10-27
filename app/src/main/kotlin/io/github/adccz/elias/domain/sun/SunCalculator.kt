package io.github.adccz.elias.domain.sun

import io.github.adccz.elias.domain.model.GeoLocation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise and sunset for one day. A value is null when the sun does not rise or set (polar day or night). */
data class SunTimes(val date: LocalDate, val sunrise: ZonedDateTime?, val sunset: ZonedDateTime?)

/**
 * Sunrise and sunset after the NOAA Solar Calculator, which is based on Jean Meeus, *Astronomical Algorithms*.
 * It is accurate to about a minute at mid latitudes.
 *
 * Everything is computed in UTC and converted to the local [zone] only at the end,
 * so summer and winter time need no special handling.
 */
class SunCalculator @Inject constructor(private val location: GeoLocation, private val zone: ZoneId) {
    fun sunTimes(date: LocalDate): SunTimes = SunTimes(
        date = date,
        sunrise = eventInstant(date, Event.SUNRISE)?.atZone(zone),
        sunset = eventInstant(date, Event.SUNSET)?.atZone(zone),
    )

    private enum class Event(val sign: Int) {
        SUNRISE(-1),
        SUNSET(+1),
    }

    private fun eventInstant(date: LocalDate, event: Event): Instant? {
        val julianDayAtMidnight = date.toEpochDay() + JULIAN_DAY_OF_UNIX_EPOCH
        // Start with the sun's position at solar noon, then compute again at the event time for better accuracy.
        var minutesAfterMidnightUtc = SOLAR_NOON_AT_GREENWICH - 4 * location.longitude
        repeat(2) {
            val julianDay = julianDayAtMidnight + minutesAfterMidnightUtc / MINUTES_PER_DAY
            minutesAfterMidnightUtc = eventMinutesUtc(julianDay, event) ?: return null
        }
        val midnightUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        return midnightUtc.plusMillis((minutesAfterMidnightUtc * 60_000).roundToLong())
    }

    /** Minutes after midnight UTC when the event happens, using the sun's position at [julianDay]. */
    private fun eventMinutesUtc(julianDay: Double, event: Event): Double? {
        val position = solarPosition(julianDay)
        val hourAngle = sunriseHourAngle(position.declination) ?: return null
        val solarNoon = SOLAR_NOON_AT_GREENWICH - 4 * location.longitude - position.equationOfTimeMinutes
        // The earth turns 1 degree in 4 minutes.
        return solarNoon + event.sign * 4 * hourAngle
    }

    private data class SolarPosition(val declination: Double, val equationOfTimeMinutes: Double)

    private fun solarPosition(julianDay: Double): SolarPosition {
        val t = (julianDay - J2000) / DAYS_PER_JULIAN_CENTURY
        val meanLongitude = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val equationOfCenter =
            sinDeg(meanAnomaly) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sinDeg(2 * meanAnomaly) * (0.019993 - 0.000101 * t) +
                sinDeg(3 * meanAnomaly) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val apparentLongitude = meanLongitude + equationOfCenter - 0.00569 - 0.00478 * sinDeg(omega)
        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = meanObliquity + 0.00256 * cosDeg(omega)
        val declination = Math.toDegrees(asin(sinDeg(obliquity) * sinDeg(apparentLongitude)))

        val y = tan(Math.toRadians(obliquity / 2)).pow(2)
        val equationOfTime = 4 * Math.toDegrees(
            y * sinDeg(2 * meanLongitude) -
                2 * eccentricity * sinDeg(meanAnomaly) +
                4 * eccentricity * y * sinDeg(meanAnomaly) * cosDeg(2 * meanLongitude) -
                0.5 * y * y * sinDeg(4 * meanLongitude) -
                1.25 * eccentricity * eccentricity * sinDeg(2 * meanAnomaly),
        )
        return SolarPosition(declination, equationOfTime)
    }

    /** Hour angle of sunrise in degrees, or null if the sun stays above or below the horizon all day. */
    private fun sunriseHourAngle(declination: Double): Double? {
        val cosHourAngle =
            cosDeg(SUNRISE_ZENITH) / (cosDeg(location.latitude) * cosDeg(declination)) -
                tanDeg(location.latitude) * tanDeg(declination)
        if (cosHourAngle !in -1.0..1.0) return null
        return Math.toDegrees(acos(cosHourAngle))
    }

    private companion object {
        const val JULIAN_DAY_OF_UNIX_EPOCH = 2440587.5
        const val J2000 = 2451545.0
        const val DAYS_PER_JULIAN_CENTURY = 36525.0
        const val MINUTES_PER_DAY = 1440.0
        const val SOLAR_NOON_AT_GREENWICH = 720.0

        /** 90° plus atmospheric refraction (34') and the sun's radius (16'): the moment the top edge appears. */
        const val SUNRISE_ZENITH = 90.833

        fun sinDeg(degrees: Double) = sin(Math.toRadians(degrees))

        fun cosDeg(degrees: Double) = cos(Math.toRadians(degrees))

        fun tanDeg(degrees: Double) = tan(Math.toRadians(degrees))
    }
}
