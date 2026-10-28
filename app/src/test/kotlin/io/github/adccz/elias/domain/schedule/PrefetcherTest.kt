package io.github.adccz.elias.domain.schedule

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.log.SourceOutcome
import io.github.adccz.elias.domain.log.SourceRecord
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.DefaultNewsFeeds
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.NewsFeed
import io.github.adccz.elias.domain.news.NewsRepository
import io.github.adccz.elias.domain.weather.WeatherForecast
import io.github.adccz.elias.domain.weather.WeatherRepository
import io.github.adccz.elias.testing.FakeRunLog
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefetcherTest {
    private val now = Instant.parse("2026-10-30T05:30:00Z")
    private val clock = Clock.fixed(now, PRAGUE_ZONE)
    private val runLog = FakeRunLog()

    private fun weather(result: SourceResult<WeatherForecast>) = object : WeatherRepository {
        override suspend fun forecast() = result
    }

    private fun news(result: (NewsFeed) -> SourceResult<List<Headline>>) = object : NewsRepository {
        override suspend fun headlines(feed: NewsFeed) = result(feed)
    }

    private val offline = SourceResult.Failure(FailureReason.NETWORK)

    @Test
    fun `loads every network source and records the run`() = runTest {
        val prefetcher = Prefetcher(
            weather = weather(SourceResult.Success(WeatherForecast(emptyList()), now)),
            news = news { offline },
            runLog = runLog,
            clock = clock,
        )

        assertTrue(prefetcher.prefetch())

        val run = runLog.runs.single()
        assertEquals(RunKind.PREFETCH, run.kind)
        assertEquals(RunTrigger.SCHEDULE, run.trigger)
        assertEquals(RunOutcome.PREFETCHED, run.outcome)
        assertEquals(SourceRecord("weather", SourceOutcome.LOADED), run.sources.first())
        assertEquals(DefaultNewsFeeds.all.size + 1, run.sources.size)
        assertTrue(
            run.sources.drop(1).all {
                it == SourceRecord(it.source, SourceOutcome.FAILED, FailureReason.NETWORK)
            },
        )
    }

    @Test
    fun `reports failure when nothing was loaded fresh`() = runTest {
        val prefetcher = Prefetcher(
            weather = weather(SourceResult.Success(WeatherForecast(emptyList()), now, fromCache = true)),
            news = news { offline },
            runLog = runLog,
            clock = clock,
        )

        assertFalse(prefetcher.prefetch())
        assertEquals(SourceOutcome.FROM_CACHE, runLog.runs.single().sources.first().outcome)
    }

    @Test
    fun `a crashing source fails alone`() = runTest {
        val prefetcher = Prefetcher(
            weather = object : WeatherRepository {
                override suspend fun forecast(): SourceResult<WeatherForecast> = error("bug")
            },
            news = news { SourceResult.Success(emptyList(), now) },
            runLog = runLog,
            clock = clock,
        )

        assertTrue(prefetcher.prefetch())
        assertEquals(SourceOutcome.FAILED, runLog.runs.single().sources.first().outcome)
    }

    @Test
    fun `a broken log does not stop the prefetch`() = runTest {
        val prefetcher = Prefetcher(
            weather = weather(SourceResult.Success(WeatherForecast(emptyList()), now)),
            news = news { offline },
            runLog = FakeRunLog(failing = true),
            clock = clock,
        )

        assertTrue(prefetcher.prefetch())
    }
}
