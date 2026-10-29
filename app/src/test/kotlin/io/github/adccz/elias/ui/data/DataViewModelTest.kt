package io.github.adccz.elias.ui.data

import app.cash.turbine.test
import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.journal.Journal
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
import io.github.adccz.elias.testing.FakeRunLog
import io.github.adccz.elias.testing.FakeTopicSettings
import io.github.adccz.elias.testing.InMemoryJournal
import io.github.adccz.elias.testing.MainDispatcherExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class DataViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-10-27T06:00:00Z"), PRAGUE_ZONE)

    /** A calendar whose permission can be granted in the middle of a test, like in system settings. */
    private class SwitchableCalendar(var permissionGranted: Boolean) : CalendarRepository {
        /** How many times today's events were asked for (each load also asks for tomorrow). */
        var calls = 0

        override suspend fun eventsOn(date: LocalDate): SourceResult<List<CalendarEvent>> {
            if (date == LocalDate.of(2026, 10, 27)) calls++
            return if (permissionGranted) {
                SourceResult.Success(emptyList(), Instant.EPOCH)
            } else {
                SourceResult.Failure(FailureReason.NO_PERMISSION)
            }
        }
    }

    // The other sources simply fail: this test is about the ViewModel, not about the data.
    private val offlineWeather = object : WeatherRepository {
        override suspend fun forecast(): SourceResult<WeatherForecast> = SourceResult.Failure(FailureReason.NETWORK)
    }
    private val missingPlan = object : PlanRepository {
        override suspend fun loadPlan(): SourceResult<LoadedPlan> = SourceResult.Failure(FailureReason.INVALID_DATA)
    }
    private val offlineNews = object : NewsRepository {
        override suspend fun headlines(feed: NewsFeed): SourceResult<List<Headline>> =
            SourceResult.Failure(FailureReason.NETWORK)
    }

    private fun viewModel(calendar: CalendarRepository): DataViewModel {
        val sun = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)
        val loader =
            TodayOverviewLoader(
                calendar,
                offlineWeather,
                missingPlan,
                offlineNews,
                sun,
                SabbathCalculator(sun),
                FakeRunLog(),
                clock,
                FakeTopicSettings(),
                Journal(InMemoryJournal(), clock),
            )
        return DataViewModel(loader)
    }

    @Test
    fun `shows loading first, then the loaded overview`() = runTest {
        val viewModel = viewModel(SwitchableCalendar(permissionGranted = true))

        viewModel.uiState.test {
            assertEquals(DataUiState.Loading, awaitItem())
            val loaded = assertInstanceOf(DataUiState.Loaded::class.java, awaitItem())
            assertFalse(loaded.isRefreshing)
            assertInstanceOf(SourceResult.Success::class.java, loaded.overview.calendar)
        }
    }

    @Test
    fun `refresh keeps the old data visible while loading`() = runTest {
        val viewModel = viewModel(SwitchableCalendar(permissionGranted = true))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertInstanceOf(DataUiState.Loaded::class.java, awaitItem())
            viewModel.refresh()
            assertEquals(true, (awaitItem() as DataUiState.Loaded).isRefreshing)
            assertEquals(false, (awaitItem() as DataUiState.Loaded).isRefreshing)
        }
    }

    @Test
    fun `returning to the screen reloads only when calendar access was missing`() = runTest {
        val calendar = SwitchableCalendar(permissionGranted = false)
        val viewModel = viewModel(calendar)
        advanceUntilIdle()
        assertEquals(1, calendar.calls)

        calendar.permissionGranted = true // the user allowed access in system settings
        viewModel.onScreenResumed()
        advanceUntilIdle()
        assertEquals(2, calendar.calls)

        viewModel.onScreenResumed() // access is there now, so nothing to reload
        advanceUntilIdle()
        assertEquals(2, calendar.calls)
    }
}
