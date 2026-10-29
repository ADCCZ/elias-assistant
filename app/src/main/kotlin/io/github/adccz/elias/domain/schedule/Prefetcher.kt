package io.github.adccz.elias.domain.schedule

import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunLog
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.log.SourceOutcome
import io.github.adccz.elias.domain.log.SourceRecord
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.model.loadGuarded
import io.github.adccz.elias.domain.news.DefaultNewsFeeds
import io.github.adccz.elias.domain.news.NewsRepository
import io.github.adccz.elias.domain.overview.TodayOverviewLoader
import io.github.adccz.elias.domain.weather.WeatherRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Loads the network sources (weather and headlines) before the morning briefing. The repositories keep each answer
 * in their cache, so in the morning the briefing has fresh data even when the network is slow or down.
 * The calendar and the plan are on the phone and need no prefetch.
 */
class Prefetcher @Inject constructor(
    private val weather: WeatherRepository,
    private val news: NewsRepository,
    private val runLog: RunLog,
    private val clock: Clock,
) {
    /** Loads every network source and records the run. True when at least one source answered just now. */
    suspend fun prefetch(): Boolean {
        val startedAt = clock.instant()
        val timeout = TodayOverviewLoader.SOURCE_TIMEOUT
        val results: List<Pair<String, SourceResult<*>>> = coroutineScope {
            val weatherResult = async { "weather" to loadGuarded(timeout) { weather.forecast() } }
            val newsResults = DefaultNewsFeeds.all.map { feed ->
                async { "news:${feed.name}" to loadGuarded(timeout) { news.headlines(feed) } }
            }
            (listOf(weatherResult) + newsResults).awaitAll()
        }
        val sources = results.map { (name, result) ->
            SourceRecord(name, result.outcome(), (result as? SourceResult.Failure)?.reason)
        }
        try {
            runLog.record(
                RunRecord(
                    RunKind.PREFETCH,
                    RunTrigger.SCHEDULE,
                    startedAt,
                    clock.instant(),
                    RunOutcome.PREFETCHED,
                    sources,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The log is for looking back. The cache is filled either way.
        }
        return sources.any { it.outcome == SourceOutcome.LOADED }
    }

    private fun SourceResult<*>.outcome(): SourceOutcome = when (this) {
        is SourceResult.Success -> if (fromCache) SourceOutcome.FROM_CACHE else SourceOutcome.LOADED
        is SourceResult.Failure -> SourceOutcome.FAILED
    }
}
