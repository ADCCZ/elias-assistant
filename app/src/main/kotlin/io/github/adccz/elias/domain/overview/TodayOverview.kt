package io.github.adccz.elias.domain.overview

import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.NewsFeed
import io.github.adccz.elias.domain.plan.DayPlan
import io.github.adccz.elias.domain.sun.SabbathWindow
import io.github.adccz.elias.domain.sun.SunTimes
import io.github.adccz.elias.domain.weather.DayWeather
import java.time.ZonedDateTime

/** Everything Elias knows about today. Each source has its own result, so one failure never hides the rest. */
data class TodayOverview(
    val loadedAt: ZonedDateTime,
    val sunTimes: SunTimes,
    val sabbath: SabbathWindow,
    val calendar: SourceResult<List<CalendarEvent>>,
    val weather: SourceResult<DayWeather>,
    val plan: SourceResult<TodayPlan>,
    val newsFeeds: Map<NewsFeed, SourceResult<List<Headline>>>,
    /** The headlines worth showing, picked from all feeds that loaded. */
    val headlines: List<Headline>,
)

/** Today's part of the plan, with the plan's name and whether it is the fictional sample. */
data class TodayPlan(val planName: String, val isSample: Boolean, val day: DayPlan)
