package io.github.adccz.elias.domain.overview

import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.journal.Journal
import io.github.adccz.elias.domain.journal.JournalDay
import io.github.adccz.elias.domain.journal.JournalStatus
import io.github.adccz.elias.domain.log.RunLog
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.model.loadGuarded
import io.github.adccz.elias.domain.news.DefaultNewsFeeds
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.NewsRepository
import io.github.adccz.elias.domain.news.headlineWindow
import io.github.adccz.elias.domain.news.selectHeadlines
import io.github.adccz.elias.domain.plan.PlanRepository
import io.github.adccz.elias.domain.plan.dayPlan
import io.github.adccz.elias.domain.sun.SabbathCalculator
import io.github.adccz.elias.domain.sun.SunCalculator
import io.github.adccz.elias.domain.topics.Topic
import io.github.adccz.elias.domain.topics.TopicSettingsRepository
import io.github.adccz.elias.domain.topics.Topics
import io.github.adccz.elias.domain.weather.WeatherRepository
import io.github.adccz.elias.domain.weather.dayWeather
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Loads all sources at the same time and puts the results together.
 *
 * Every source runs guarded ([loadGuarded]): it gets a time limit, and an unexpected exception
 * becomes a failure of that source only.
 */
class TodayOverviewLoader @Inject constructor(
    private val calendar: CalendarRepository,
    private val weather: WeatherRepository,
    private val plans: PlanRepository,
    private val news: NewsRepository,
    private val sun: SunCalculator,
    private val sabbath: SabbathCalculator,
    private val runLog: RunLog,
    private val clock: Clock,
    private val topicSettings: TopicSettingsRepository,
    private val journal: Journal,
) {
    suspend fun load(): TodayOverview = coroutineScope {
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()

        val calendarResult = async { guarded { calendar.eventsOn(today) } }
        // The evening briefing talks about tomorrow.
        val tomorrowCalendarResult = async { guarded { calendar.eventsOn(today.plusDays(1)) } }
        val weatherResult = async {
            when (val result = guarded { weather.forecast() }) {
                is SourceResult.Failure -> result

                is SourceResult.Success -> result.data.dayWeather(today)
                    ?.let { SourceResult.Success(it, result.loadedAt, result.fromCache) }
                    ?: SourceResult.Failure(FailureReason.INVALID_DATA, "The forecast has no hours for $today")
            }
        }
        val planResult = async {
            guarded { plans.loadPlan() }.map { loaded ->
                TodayPlan(
                    planName = loaded.plan.name,
                    validFrom = loaded.plan.validFrom,
                    validTo = loaded.plan.validTo,
                    isSample = loaded.isSample,
                    day = loaded.plan.dayPlan(today),
                )
            }
        }
        val topicsResult = async { loadTopics() }
        val journalResult = async { loadJournal(now) }
        val newsResults = DefaultNewsFeeds.all.map { feed -> async { feed to guarded { news.headlines(feed) } } }

        val newsFeeds = newsResults.awaitAll().toMap()
        val allHeadlines = newsFeeds.values.filterIsInstance<SourceResult.Success<List<Headline>>>().flatMap { it.data }

        TodayOverview(
            loadedAt = now,
            sunTimes = sun.sunTimes(today),
            sabbath = sabbath.currentOrNext(now),
            calendar = calendarResult.await(),
            tomorrowCalendar = tomorrowCalendarResult.await(),
            weather = weatherResult.await(),
            plan = planResult.await(),
            newsFeeds = newsFeeds,
            headlines = selectHeadlines(
                allHeadlines,
                now.toInstant(),
                headlineWindow(lastBriefingAt(), now.toInstant()),
            ),
            topics = topicsResult.await(),
            journal = journalResult.await(),
        )
    }

    /** Today's journal entry and yesterday's step. A broken journal only means the briefing leaves it out. */
    private suspend fun loadJournal(now: ZonedDateTime): JournalStatus = try {
        val day = JournalDay.of(now)
        JournalStatus(writtenToday = journal.isWritten(day), reminder = journal.reminderFor(day))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        JournalStatus.UNKNOWN
    }

    /**
     * The calendars the user marked as topics, with their current names. Without topics, or when the calendars
     * cannot be listed, the briefing simply has no plans by topic.
     */
    private suspend fun loadTopics(): List<Topic> {
        val ids = try {
            topicSettings.topicCalendarIds.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
        if (ids.isEmpty()) return emptyList()
        val calendars = guarded { calendar.calendars() } as? SourceResult.Success ?: return emptyList()
        return Topics.resolve(calendars.data, ids)
    }

    /** When the last briefing played. A broken log only means the full day of headlines. */
    private suspend fun lastBriefingAt(): Instant? = try {
        runLog.lastPlayedBriefingAt()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun <T> guarded(block: suspend () -> SourceResult<T>): SourceResult<T> =
        loadGuarded(SOURCE_TIMEOUT, block)

    private inline fun <T, R> SourceResult<T>.map(transform: (T) -> R): SourceResult<R> = when (this) {
        is SourceResult.Success -> SourceResult.Success(transform(data), loadedAt, fromCache)
        is SourceResult.Failure -> this
    }

    companion object {
        /** Longer than the HTTP timeout with retries would take on a slow connection, short enough to wait for. */
        val SOURCE_TIMEOUT: Duration = 20.seconds
    }
}
