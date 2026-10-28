package io.github.adccz.elias.ui.preview

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.briefing.AiBriefingBuilder
import io.github.adccz.elias.domain.briefing.AiBriefingResult
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.briefing.EveningInput
import io.github.adccz.elias.domain.briefing.MorningInput
import io.github.adccz.elias.domain.briefing.TemplateBriefingBuilder
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
import io.github.adccz.elias.testing.MainDispatcherExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PreviewViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-10-27T06:00:00Z"), PRAGUE_ZONE)

    /** Every data source fails: the test is about the screen logic, not the data. */
    private val loader: TodayOverviewLoader = run {
        val sun = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)
        TodayOverviewLoader(
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
            clock = clock,
        )
    }

    private class NoCreditAi : AiBriefingBuilder {
        var calls = 0

        override suspend fun morning(input: MorningInput): AiBriefingResult {
            calls++
            return AiBriefingResult.Failure(AiFailureReason.NO_CREDIT)
        }

        override suspend fun evening(input: EveningInput): AiBriefingResult {
            calls++
            return AiBriefingResult.Failure(AiFailureReason.NO_CREDIT)
        }
    }

    @Test
    fun `writes both briefings and explains why the template was used`() = runTest {
        val ai = NoCreditAi()
        val viewModel = PreviewViewModel(loader, BriefingService(TemplateBriefingBuilder(), ai))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(BriefingSource.TEMPLATE, state.morning?.source)
        assertEquals(AiFailureReason.NO_CREDIT, state.evening?.aiFailure?.reason)
        assertEquals(2, ai.calls)
    }

    @Test
    fun `switching AI off writes from the template without asking the AI`() = runTest {
        val ai = NoCreditAi()
        val viewModel = PreviewViewModel(loader, BriefingService(TemplateBriefingBuilder(), ai))
        advanceUntilIdle()

        viewModel.setUseAi(false)
        advanceUntilIdle()

        assertEquals(AiFailureReason.DISABLED, viewModel.uiState.value.morning?.aiFailure?.reason)
        assertEquals(2, ai.calls) // only the first generation asked the AI
    }

    @Test
    fun `the selected tab decides which briefing is shown`() = runTest {
        val viewModel = PreviewViewModel(loader, BriefingService(TemplateBriefingBuilder(), NoCreditAi()))
        advanceUntilIdle()

        viewModel.select(BriefingKind.EVENING)

        assertEquals(BriefingKind.EVENING, viewModel.uiState.value.current?.briefing?.kind)
    }
}
