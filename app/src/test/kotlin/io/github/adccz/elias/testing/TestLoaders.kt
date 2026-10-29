package io.github.adccz.elias.testing

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.NewsFeed
import io.github.adccz.elias.domain.news.NewsRepository
import io.github.adccz.elias.domain.overview.TodayOverviewLoader
import io.github.adccz.elias.domain.plan.LoadedPlan
import io.github.adccz.elias.domain.plan.PlanRepository
import io.github.adccz.elias.domain.sun.SabbathCalculator
import io.github.adccz.elias.domain.sun.SunCalculator
import io.github.adccz.elias.domain.weather.WeatherForecast
import io.github.adccz.elias.domain.weather.WeatherRepository
import java.time.Clock
import java.time.LocalDate

/** Loaders for screen tests, which are about the screen logic, not the data. */
object TestLoaders {
    /** Every source fails, so the briefings come from the template's "nothing known" sentences. */
    fun failing(clock: Clock): TodayOverviewLoader {
        val sun = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)
        return TodayOverviewLoader(
            calendar = object : CalendarRepository {
                override suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>> =
                    SourceResult.Failure(FailureReason.NO_PERMISSION)
            },
            weather = object : WeatherRepository {
                override suspend fun forecast(): SourceResult<WeatherForecast> =
                    SourceResult.Failure(FailureReason.NETWORK)
            },
            plans = object : PlanRepository {
                override suspend fun loadPlan(): SourceResult<LoadedPlan> =
                    SourceResult.Failure(FailureReason.INVALID_DATA)
            },
            news = object : NewsRepository {
                override suspend fun headlines(feed: NewsFeed): SourceResult<List<Headline>> =
                    SourceResult.Failure(FailureReason.NETWORK)
            },
            sun = sun,
            sabbath = SabbathCalculator(sun),
            runLog = FakeRunLog(),
            clock = clock,
            topicSettings = FakeTopicSettings(),
        )
    }
}
