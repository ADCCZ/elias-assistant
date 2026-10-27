package io.github.adccz.elias.domain.weather

import java.time.LocalDate
import java.time.LocalTime

/** A short summary of one day's weather, enough for a spoken briefing. */
data class DayWeather(
    val date: LocalDate,
    /** Average from 6:00 to 9:00. */
    val morningTemperature: Double?,
    /** Maximum from 12:00 to 18:00. */
    val afternoonTemperature: Double?,
    /** The most common sky during the day (6:00 to 21:00). On a tie the cloudier one wins. */
    val sky: WeatherCondition?,
    /** The most significant precipitation during the day, or null when it stays dry. */
    val precipitation: WeatherCondition?,
    /** When [precipitation] first appears. */
    val precipitationFrom: LocalTime?,
    val precipitationMm: Double,
    val maxPrecipitationProbabilityPercent: Int?,
    val maxWindKmh: Double?,
    val maxGustKmh: Double?,
)

private val MORNING = LocalTime.of(6, 0)..<LocalTime.of(9, 0)
private val AFTERNOON = LocalTime.of(12, 0)..<LocalTime.of(18, 0)
private val DAYTIME = LocalTime.of(6, 0)..<LocalTime.of(21, 0)

/**
 * Summarizes [date] from the hourly forecast, or returns null when the forecast has no hours for that day.
 *
 * Hours are matched by their local time, not by their position in the list.
 * That matters on DST days, which have 23 or 25 hours.
 */
fun WeatherForecast.dayWeather(date: LocalDate): DayWeather? {
    val day = hours.filter { it.time.toLocalDate() == date }
    if (day.isEmpty()) return null

    fun inPeriod(period: OpenEndRange<LocalTime>) = day.filter { it.time.toLocalTime() in period }
    val daytime = inPeriod(DAYTIME)

    val precipitationHours = daytime.filter { it.condition?.isPrecipitation == true }
    val precipitation = precipitationHours.mapNotNull { it.condition }.maxOrNull()

    return DayWeather(
        date = date,
        morningTemperature = inPeriod(MORNING).mapNotNull { it.temperatureCelsius }.averageOrNull(),
        afternoonTemperature = inPeriod(AFTERNOON).mapNotNull { it.temperatureCelsius }.maxOrNull(),
        sky = daytime
            .mapNotNull { it.condition }
            .filterNot { it.isPrecipitation }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<WeatherCondition, Int>> { it.value }.thenBy { it.key.ordinal })
            ?.key,
        precipitation = precipitation,
        precipitationFrom = precipitationHours.firstOrNull { it.condition == precipitation }?.time?.toLocalTime(),
        precipitationMm = daytime.sumOf { it.precipitationMm ?: 0.0 },
        maxPrecipitationProbabilityPercent = daytime.mapNotNull { it.precipitationProbabilityPercent }.maxOrNull(),
        maxWindKmh = daytime.mapNotNull { it.windSpeedKmh }.maxOrNull(),
        maxGustKmh = daytime.mapNotNull { it.windGustsKmh }.maxOrNull(),
    )
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
