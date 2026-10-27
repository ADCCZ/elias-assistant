package io.github.adccz.elias.ui.data

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.model.FailureReason
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
import io.github.adccz.elias.domain.sun.SabbathWindow
import io.github.adccz.elias.domain.sun.SunTimes
import io.github.adccz.elias.domain.weather.DayWeather
import io.github.adccz.elias.domain.weather.WeatherCondition
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Fictional data for Compose previews in Android Studio. Not used by the running app. */
internal object DataPreviewData {
    fun overview(): TodayOverview {
        val day = LocalDate.of(2026, 10, 27)
        fun at(date: LocalDate, hour: Int, minute: Int = 0): ZonedDateTime =
            date.atTime(hour, minute).atZone(PRAGUE_ZONE)
        val now = at(day, 7)
        val loadedAt = now.toInstant()

        return TodayOverview(
            loadedAt = now,
            sunTimes = SunTimes(day, sunrise = at(day, 6, 47), sunset = at(day, 16, 53)),
            sabbath = SabbathWindow(start = at(day.plusDays(3), 16, 48), end = at(day.plusDays(4), 16, 46)),
            calendar = SourceResult.Success(
                listOf(
                    CalendarEvent("Státní svátek", at(day, 0), at(day.plusDays(1), 0), true, null, "Svátky"),
                    CalendarEvent("Databáze – přednáška", at(day, 9), at(day, 10, 30), false, "UP-105", "Škola"),
                ),
                loadedAt,
            ),
            weather = SourceResult.Success(
                DayWeather(
                    date = day,
                    morningTemperature = 3.2,
                    afternoonTemperature = 11.4,
                    sky = WeatherCondition.PARTLY_CLOUDY,
                    precipitation = WeatherCondition.LIGHT_RAIN,
                    precipitationFrom = LocalTime.of(15, 0),
                    precipitationMm = 1.8,
                    maxPrecipitationProbabilityPercent = 70,
                    maxWindKmh = 14.0,
                    maxGustKmh = 31.0,
                ),
                loadedAt,
            ),
            plan = SourceResult.Success(
                TodayPlan(
                    planName = "Ukázkový plán: zimní semestr",
                    validFrom = day.minusDays(20),
                    validTo = day.plusDays(90),
                    isSample = true,
                    day = DayPlan(
                        date = day,
                        isPlanValid = true,
                        phase = Phase("Rozjezd semestru", day.minusDays(20), day.plusDays(30)),
                        dailyMinimum = listOf("Vstát do sedmi", "Jeden hlavní úkol"),
                        rules = listOf("Telefon mimo ložnici"),
                        weeklyTheme = WeeklyTheme(day.minusDays(1), "Vděčnost", "Každý večer zapsat tři dobré věci"),
                        morningReading = "Př 2",
                        saturdayStudy = null,
                        items = listOf(DayItem("Běh", "Aspoň pět kilometrů")),
                        deadlinesToday = emptyList(),
                        deadlinesTomorrow = listOf(Deadline(day.plusDays(1), "Odevzdat první úkol", "Databáze", true)),
                    ),
                ),
                loadedAt,
            ),
            newsFeeds = DefaultNewsFeeds.all.associateWith { feed ->
                if (feed.name == "Root.cz") {
                    SourceResult.Failure(FailureReason.NETWORK)
                } else {
                    SourceResult.Success(emptyList<Headline>(), loadedAt)
                }
            },
            headlines = listOf(
                Headline("1", "Město otevřelo novou knihovnu", "ČT24", null, loadedAt.minusSeconds(1800)),
                Headline(
                    "2",
                    "Silnice mezi dvěma vesnicemi bude do pátku uzavřená",
                    "iROZHLAS",
                    null,
                    loadedAt.minusSeconds(5400),
                ),
            ),
        )
    }
}
