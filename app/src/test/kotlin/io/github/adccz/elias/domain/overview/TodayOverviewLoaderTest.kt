package io.github.adccz.elias.domain.overview

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import io.github.adccz.elias.domain.journal.Journal
import io.github.adccz.elias.domain.journal.JournalEntry
import io.github.adccz.elias.domain.journal.JournalReminder
import io.github.adccz.elias.domain.journal.JournalStatus
import io.github.adccz.elias.domain.journal.JournalText
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.NewsFeed
import io.github.adccz.elias.domain.news.NewsRepository
import io.github.adccz.elias.domain.plan.LoadedPlan
import io.github.adccz.elias.domain.plan.Plan
import io.github.adccz.elias.domain.plan.PlanRepository
import io.github.adccz.elias.domain.reading.BibleReading
import io.github.adccz.elias.domain.reading.ReadChapter
import io.github.adccz.elias.domain.reading.ReadingLogRepository
import io.github.adccz.elias.domain.reading.ReadingQueue
import io.github.adccz.elias.domain.reading.ReadingStatus
import io.github.adccz.elias.domain.routine.MorningRoutine
import io.github.adccz.elias.domain.routine.Recipes
import io.github.adccz.elias.domain.sun.SabbathCalculator
import io.github.adccz.elias.domain.sun.SunCalculator
import io.github.adccz.elias.domain.topics.Topic
import io.github.adccz.elias.domain.weather.HourlyWeather
import io.github.adccz.elias.domain.weather.WeatherCondition
import io.github.adccz.elias.domain.weather.WeatherForecast
import io.github.adccz.elias.domain.weather.WeatherRepository
import io.github.adccz.elias.testing.FakeCalendarSettings
import io.github.adccz.elias.testing.FakeRunLog
import io.github.adccz.elias.testing.FixedRoutineContent
import io.github.adccz.elias.testing.InMemoryJournal
import io.github.adccz.elias.testing.InMemoryReadingLog
import io.github.adccz.elias.testing.InMemoryReadingQueue
import io.github.adccz.elias.testing.TestRoutine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        runLog: FakeRunLog = FakeRunLog(),
        calendarSettings: FakeCalendarSettings = FakeCalendarSettings(),
        journal: InMemoryJournal = InMemoryJournal(),
        readingQueue: InMemoryReadingQueue = InMemoryReadingQueue(),
        readingLog: InMemoryReadingLog = InMemoryReadingLog(),
        routineContent: FixedRoutineContent = FixedRoutineContent(),
    ) = TodayOverviewLoader(
        calendar,
        weather,
        plans,
        news,
        sun,
        SabbathCalculator(sun),
        runLog,
        clock,
        calendarSettings,
        Journal(journal, clock),
        BibleReading(readingQueue, readingLog, plans, clock),
        MorningRoutine(routineContent, routineContent, clock),
    )

    @Test
    fun `knows whether today's journal entry exists and yesterday's step`() = runTest {
        val journal = InMemoryJournal(
            JournalEntry(today.minusDays(1), JournalText(step = "Zeptej se na jednu věc."), Instant.EPOCH),
        )

        val status = loader(journal = journal).load().journal

        assertEquals(
            JournalStatus(writtenToday = false, reminder = JournalReminder("Zeptej se na jednu věc.", true)),
            status,
        )
    }

    @Test
    fun `knows the morning routine of the day`() = runTest {
        val mark = BibleBooks.byAbbreviation("Mk")!!
        val queue = InMemoryReadingQueue(ReadingQueue().add(mark, 3, 16))

        val routine = loader(readingQueue = queue).load().routine

        assertEquals(ReadingStatus(Chapter(mark, 3), readToday = false, finished = false), routine.reading)
        assertEquals(TestRoutine.exercises, routine.exercises)
        assertEquals(Recipes.forDay(TestRoutine.recipes, today), routine.recipes?.today)
    }

    @Test
    fun `a broken routine part is only left out`() = runTest {
        val broken = object : ReadingLogRepository by InMemoryReadingLog() {
            override fun readOn(day: LocalDate): Flow<List<ReadChapter>> = error("Database closed")
        }
        val overview = TodayOverviewLoader(
            workingCalendar, workingWeather, workingPlans, workingNews, sun, SabbathCalculator(sun), FakeRunLog(),
            clock, FakeCalendarSettings(), Journal(InMemoryJournal(), clock),
            BibleReading(InMemoryReadingQueue(), broken, workingPlans, clock),
            FixedRoutineContent(exercises = null).let { MorningRoutine(it, it, clock) },
        ).load()

        assertNull(overview.routine.reading)
        assertNull(overview.routine.exercises)
        assertEquals(Recipes.forDay(TestRoutine.recipes, today), overview.routine.recipes?.today)
        assertInstanceOf(SourceResult.Success::class.java, overview.weather)
    }

    @Test
    fun `names the topics from the phone's calendars`() = runTest {
        val calendarWithTopics = object : CalendarRepository by workingCalendar {
            override suspend fun calendars() = SourceResult.Success(
                listOf(
                    PhoneCalendar(1, "Osobní", "olda@example.com", null, isShown = true),
                    PhoneCalendar(2, "Škola", "olda@example.com", null, isShown = true),
                    PhoneCalendar(3, "Seberozvoj", "olda@example.com", null, isShown = true),
                ),
                clock.instant(),
            )
        }

        val overview = loader(calendarWithTopics, calendarSettings = FakeCalendarSettings(setOf(2L, 3L, 99L))).load()
        val withHidden = loader(calendarWithTopics, calendarSettings = FakeCalendarSettings(setOf(2L, 3L), setOf(3L)))
            .load()

        assertEquals(listOf(Topic(3, "Seberozvoj"), Topic(2, "Škola")), overview.topics)
        assertEquals(listOf(Topic(2, "Škola")), withHidden.topics)
    }

    @Test
    fun `has no topics when the calendars cannot be listed`() = runTest {
        val overview = loader(calendarSettings = FakeCalendarSettings(setOf(2L))).load()

        assertEquals(emptyList<Topic>(), overview.topics)
    }

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

    @Test
    fun `headlines go back only to the last briefing that played`() = runTest {
        val twoHeadlines = object : NewsRepository {
            override suspend fun headlines(feed: NewsFeed): SourceResult<List<Headline>> = SourceResult.Success(
                listOf(
                    Headline(
                        "${feed.name}-old",
                        "Starší zpráva ${feed.name}",
                        feed.name,
                        null,
                        clock.instant().minusSeconds(10 * 3600),
                    ),
                    Headline(
                        "${feed.name}-new",
                        "Nová zpráva ${feed.name}",
                        feed.name,
                        null,
                        clock.instant().minusSeconds(3600),
                    ),
                ),
                clock.instant(),
            )
        }
        val sevenHoursAgo = clock.instant().minusSeconds(7 * 3600)
        val runLog = FakeRunLog().apply {
            record(
                RunRecord(
                    RunKind.EVENING_BRIEFING,
                    RunTrigger.SCHEDULE,
                    sevenHoursAgo,
                    sevenHoursAgo,
                    RunOutcome.PLAYED,
                ),
            )
        }

        val sinceEvening = loader(news = twoHeadlines, runLog = runLog).load()
        val noBriefingYet = loader(news = twoHeadlines).load()

        assertTrue(sinceEvening.headlines.all { it.id.endsWith("-new") })
        assertEquals(6, noBriefingYet.headlines.size)
    }
}
