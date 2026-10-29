package io.github.adccz.elias.domain.overview

import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.NewsFeed
import io.github.adccz.elias.domain.plan.DayPlan
import io.github.adccz.elias.domain.sun.SabbathWindow
import io.github.adccz.elias.domain.sun.SunTimes
import io.github.adccz.elias.domain.weather.DayWeather
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** Everything Elias knows about today. Each source has its own result, so one failure never hides the rest. */
data class TodayOverview(
    val loadedAt: ZonedDateTime,
    val sunTimes: SunTimes,
    val sabbath: SabbathWindow,
    val calendar: SourceResult<List<CalendarEvent>>,
    val tomorrowCalendar: SourceResult<List<CalendarEvent>>,
    val weather: SourceResult<DayWeather>,
    val plan: SourceResult<TodayPlan>,
    val newsFeeds: Map<NewsFeed, SourceResult<List<Headline>>>,
    /** The headlines worth showing, picked from all feeds that loaded. */
    val headlines: List<Headline>,
)

/** Today's part of the plan, with the plan's name, its validity and whether it is the fictional sample. */
data class TodayPlan(
    val planName: String,
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val isSample: Boolean,
    val day: DayPlan,
)

/**
 * True on Saturday from sunset on: the sabbath has just ended. The evening briefing then opens with
 * "the sabbath has ended" (spec, sabbath rules).
 */
val TodayOverview.sabbathJustEnded: Boolean
    get() {
        val sunset = sunTimes.sunset ?: return false
        return loadedAt.dayOfWeek == DayOfWeek.SATURDAY && !loadedAt.isBefore(sunset)
    }
