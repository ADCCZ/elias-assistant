package io.github.adccz.elias.domain.overview

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.NewsFeed
import io.github.adccz.elias.domain.news.NewsRepository
import io.github.adccz.elias.domain.plan.LoadedPlan
import io.github.adccz.elias.domain.plan.Plan
import io.github.adccz.elias.domain.plan.PlanRepository
import io.github.adccz.elias.domain.sun.SabbathCalculator
import io.github.adccz.elias.domain.sun.SunCalculator
import io.github.adccz.elias.domain.weather.HourlyWeather
import io.github.adccz.elias.domain.weather.WeatherCondition
import io.github.adccz.elias.domain.weather.WeatherForecast
import io.github.adccz.elias.domain.weather.WeatherRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class TodayOverviewLoaderTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-27T06:00:00Z"), PRAGUE_ZONE) // Tuesday 7:00
    private val today = LocalDate.of(2026, 10, 27)
    private val sun = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)

    // Fake sources. Each test replaces only the one it is about.

    private val workingCalendar = object : CalendarRepository {
        override suspend fun eventsOn(date: LocalDate) = SourceResult.Success(
            listOf(
                CalendarEvent(
                    title = "Přednáška",
                    start = date.atTime(9, 0).atZone(PRAGUE_ZONE),
                    end = date.atTime(10, 30).atZone(PRAGUE_ZONE),
                    isAllDay = false,
                    location = null,
                    calendarName = null,
                ),
            ),
            clock.instant(),
        )
    }

    private fun forecastFor(day: LocalDate) = WeatherForecast(
        (0..23).map { hour ->
            HourlyWeather(
                time = LocalDateTime.of(day, java.time.LocalTime.of(hour, 0)).atZone(PRAGUE_ZONE),
                temperatureCelsius = 10.0,
                precipitationMm = 0.0,
                precipitationProbabilityPercent = 0,
                condition = WeatherCondition.CLEAR,
                windSpeedKmh = 5.0,
                windGustsKmh = 10.0,
            )
        },
    )

    private val workingWeather = object : WeatherRepository {
        override suspend fun forecast() = SourceResult.Success(forecastFor(today), clock.instant())
    }

    private val workingPlans = object : PlanRepository {
        override suspend fun loadPlan() = SourceResult.Success(
            LoadedPlan(
                Plan(
                    name = "Test",
                    validFrom = today.minusDays(7),
                    validTo = today.plusDays(7),
                    phases = emptyList(),
                    dailyMinimum = listOf("Vstát do sedmi"),
                    rules = emptyList(),
                    weeklyThemes = emptyList(),
                    morningReadings = emptyList(),
                    saturdayStudies = emptyList(),
                    recurringItems = emptyList(),
                    oneTimeItems = emptyList(),
                    deadlines = emptyList(),
                ),
                isSample = true,
            ),
            clock.instant(),
        )
    }

    private val workingNews = object : NewsRepository {
        override suspend fun headlines(feed: NewsFeed) = SourceResult.Success(
            listOf(
                Headline(
                    "${feed.name}-1",
                    "Zpráva z ${feed.name}",
                    feed.name,
                    null,
                    clock.instant().minusSeconds(3600),
                ),
            ),
            clock.instant(),
        )
    }

    private fun loader(
        calendar: CalendarRepository = workingCalendar,
        weather: WeatherRepository = workingWeather,
        plans: PlanRepository = workingPlans,
        news: NewsRepository = workingNews,
    ) = TodayOverviewLoader(calendar, weather, plans, news, sun, SabbathCalculator(sun), clock)

    @Test
    fun `puts all sources together for today`() = runTest {
        val overview = loader().load()

        assertEquals(today, overview.loadedAt.toLocalDate())
        assertEquals(today, overview.sunTimes.date)
        assertEquals(LocalDate.of(2026, 10, 30), overview.sabbath.start.toLocalDate())
        assertEquals("Přednáška", (overview.calendar as SourceResult.Success).data.single().title)
        assertEquals(10.0, (overview.weather as SourceResult.Success).data.afternoonTemperature)
        val plan = (overview.plan as SourceResult.Success).data
        assertEquals(listOf("Vstát do sedmi"), plan.day.dailyMinimum)
        assertEquals(true, plan.isSample)
        assertEquals(3, overview.headlines.size) // one from each default feed
    }

    @Test
    fun `a crashing source fails alone`() = runTest {
        val broken = object : CalendarRepository {
            override suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>> = error("Database closed")
        }

        val overview = loader(calendar = broken).load()

        assertEquals(FailureReason.UNKNOWN, (overview.calendar as SourceResult.Failure).reason)
        assertInstanceOf(SourceResult.Success::class.java, overview.weather)
        assertInstanceOf(SourceResult.Success::class.java, overview.plan)
    }

    @Test
    fun `a source that never answers times out`() = runTest {
        val silent = object : WeatherRepository {
            override suspend fun forecast(): SourceResult<WeatherForecast> = awaitCancellation()
        }

        // runTest skips the 20 seconds of waiting in virtual time.
        val overview = loader(weather = silent).load()

        assertEquals(FailureReason.TIMEOUT, (overview.weather as SourceResult.Failure).reason)
        assertInstanceOf(SourceResult.Success::class.java, overview.calendar)
    }

    @Test
    fun `a forecast without today becomes INVALID_DATA`() = runTest {
        val stale = object : WeatherRepository {
            override suspend fun forecast() = SourceResult.Success(forecastFor(today.minusDays(3)), clock.instant())
        }

        val overview = loader(weather = stale).load()

        assertEquals(FailureReason.INVALID_DATA, (overview.weather as SourceResult.Failure).reason)
    }

    @Test
    fun `headlines come only from feeds that loaded`() = runTest {
        val partlyOffline = object : NewsRepository {
            override suspend fun headlines(feed: NewsFeed): SourceResult<List<Headline>> =
                if (feed.name == "Root.cz") SourceResult.Failure(FailureReason.NETWORK) else workingNews.headlines(feed)
        }

        val overview = loader(news = partlyOffline).load()

        assertEquals(setOf("ČT24", "iROZHLAS"), overview.headlines.map { it.source }.toSet())
        assertEquals(3, overview.newsFeeds.size)
    }
}
