package io.github.adccz.elias.testing

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.DefaultNewsFeeds
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.overview.TodayOverview
import io.github.adccz.elias.domain.overview.TodayPlan
import io.github.adccz.elias.domain.plan.DayItem
import io.github.adccz.elias.domain.plan.DayPlan
import io.github.adccz.elias.domain.plan.Deadline
import io.github.adccz.elias.domain.plan.Phase
import io.github.adccz.elias.domain.plan.WeeklyTheme
import io.github.adccz.elias.domain.sun.SabbathCalculator
import io.github.adccz.elias.domain.sun.SunCalculator
import io.github.adccz.elias.domain.weather.DayWeather
import io.github.adccz.elias.domain.weather.WeatherCondition
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Fictional [TodayOverview] values for briefing tests: a rich day with every source loaded,
 * and an empty day where everything failed. Tuesday 27 October 2026, 7:00 in Prague.
 */
object TestOverviews {
    val day: LocalDate = LocalDate.of(2026, 10, 27)
    val now: ZonedDateTime = day.atTime(7, 0).atZone(PRAGUE_ZONE)
    private val loadedAt = now.toInstant()
    private val sun = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(PRAGUE_ZONE)

    fun event(title: String, hour: Int, minute: Int = 0, location: String? = null, date: LocalDate = day) =
        CalendarEvent(title, at(date, hour, minute), at(date, hour + 1, minute), false, location, "Škola")

    fun allDay(title: String, date: LocalDate = day) =
        CalendarEvent(title, at(date, 0), at(date.plusDays(1), 0), true, null, "Svátky")

    val richWeather = DayWeather(
        date = day,
        morningTemperature = 3.2,
        afternoonTemperature = 13.6,
        sky = WeatherCondition.MAINLY_CLEAR,
        precipitation = WeatherCondition.LIGHT_RAIN,
        precipitationFrom = LocalTime.of(15, 0),
        precipitationMm = 1.8,
        maxPrecipitationProbabilityPercent = 70,
        maxWindKmh = 20.0,
        maxGustKmh = 55.0,
    )

    val richDayPlan = DayPlan(
        date = day,
        isPlanValid = true,
        phase = Phase("Rozjezd", day.minusDays(20), day.plusDays(10)),
        dailyMinimum = listOf("Vstát do sedmi", "Jeden hlavní úkol"),
        rules = listOf("Telefon mimo ložnici"),
        weeklyTheme = WeeklyTheme(day.minusDays(1), "Trpělivost", "Nechat druhé dokončit myšlenku"),
        morningReading = "Mk 1",
        saturdayStudy = null,
        items = listOf(DayItem("Angličtina"), DayItem("Běh")),
        deadlinesToday = emptyList(),
        deadlinesTomorrow = listOf(Deadline(day.plusDays(1), "Odevzdat první úkol", "Databáze", mandatory = true)),
    )

    private val richHeadlines = listOf(
        Headline("1", "Město otevřelo novou knihovnu", "ČT24", null, loadedAt.minusSeconds(1_800)),
        Headline(
            "2",
            "Silnice mezi dvěma vesnicemi bude do pátku uzavřená.",
            "iROZHLAS",
            null,
            loadedAt.minusSeconds(3_600),
        ),
        Headline("3", "Vědci popsali nový druh brouka", "ČT24", null, loadedAt.minusSeconds(5_400)),
        Headline("4", "Nová verze jádra Linuxu zrychluje start", "Root.cz", null, loadedAt.minusSeconds(7_200)),
    )

    fun rich(
        events: List<CalendarEvent> = listOf(allDay("Státní svátek"), event("Přednáška z databází", 9, 15, "UP-105")),
        tomorrowEvents: List<CalendarEvent> = listOf(event("Cvičení", 10, date = day.plusDays(1))),
        dayPlan: DayPlan = richDayPlan,
        headlines: List<Headline> = richHeadlines,
    ) = TodayOverview(
        loadedAt = now,
        sunTimes = sun.sunTimes(day),
        sabbath = SabbathCalculator(sun).currentOrNext(now),
        calendar = SourceResult.Success(events, loadedAt),
        tomorrowCalendar = SourceResult.Success(tomorrowEvents, loadedAt),
        weather = SourceResult.Success(richWeather, loadedAt),
        plan = SourceResult.Success(
            TodayPlan("Test", day.minusDays(20), day.plusDays(30), isSample = false, day = dayPlan),
            loadedAt,
        ),
        newsFeeds = DefaultNewsFeeds.all.associateWith { SourceResult.Success(emptyList<Headline>(), loadedAt) },
        headlines = headlines,
    )

    /** A day where every source failed. */
    fun empty() = TodayOverview(
        loadedAt = now,
        sunTimes = sun.sunTimes(day),
        sabbath = SabbathCalculator(sun).currentOrNext(now),
        calendar = SourceResult.Failure(FailureReason.NO_PERMISSION),
        tomorrowCalendar = SourceResult.Failure(FailureReason.NO_PERMISSION),
        weather = SourceResult.Failure(FailureReason.NETWORK),
        plan = SourceResult.Failure(FailureReason.INVALID_DATA),
        newsFeeds = DefaultNewsFeeds.all.associateWith { SourceResult.Failure(FailureReason.NETWORK) },
        headlines = emptyList(),
    )
}
