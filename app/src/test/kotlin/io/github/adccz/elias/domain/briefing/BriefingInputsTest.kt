package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.pickForBriefing
import io.github.adccz.elias.domain.routine.RoutineStatus
import io.github.adccz.elias.testing.TestOverviews
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BriefingInputsTest {
    @Test
    fun `morning input speaks dates, times, temperatures and readings`() {
        val input = BriefingInputs.morning(TestOverviews.rich())

        assertEquals("úterý sedmadvacátého října", input.dateSpoken)
        assertEquals("tři stupně", input.weather?.morningTemperature)
        assertEquals("čtrnáct stupňů", input.weather?.afternoonTemperature)
        assertEquals("skoro jasno", input.weather?.sky)
        assertEquals("slabý déšť", input.weather?.precipitation)
        assertEquals("kolem třetí odpoledne", input.weather?.precipitationFrom)
        assertEquals("sedmdesát procent", input.weather?.precipitationProbability)
        assertEquals("pětapadesát kilometrů za hodinu", input.weather?.strongWindGusts)
        assertEquals(listOf("celý den", "ve čtvrt na deset"), input.events?.map { it.time })
        assertEquals("Odevzdat první úkol", input.deadlinesTomorrow.single().title)
    }

    @Test
    fun `light wind is not mentioned`() {
        val overview = TestOverviews.rich()
        val calm = overview.copy(
            weather = io.github.adccz.elias.domain.model.SourceResult.Success(
                TestOverviews.richWeather.copy(maxGustKmh = 30.0),
                Instant.EPOCH,
            ),
        )

        assertNull(BriefingInputs.morning(calm).weather?.strongWindGusts)
    }

    @Test
    fun `failed sources are marked as unavailable, not as empty`() {
        val input = BriefingInputs.morning(TestOverviews.empty())

        assertNull(input.weather)
        assertNull(input.events)
        assertNull(input.plan)
        assertTrue(input.planUnavailable)
        assertTrue(input.headlinesUnavailable)
    }

    @Test
    fun `evening input looks at tomorrow`() {
        val input = BriefingInputs.evening(TestOverviews.rich())

        assertEquals("středa osmadvacátého října", input.tomorrowSpoken)
        assertEquals("v deset", input.tomorrowEvents?.single()?.time)
        assertEquals("Trpělivost", input.theme?.title)
    }

    @Test
    fun `headlines take turns between sources`() {
        fun headline(id: String, source: String) = Headline(id, "Titulek $id", source, null, null)
        val newestFirst = listOf(
            headline("a", "ČT24"),
            headline("b", "ČT24"),
            headline("c", "ČT24"),
            headline("d", "iROZHLAS"),
            headline("e", "Root.cz"),
        )

        val picked = pickForBriefing(newestFirst, listOf("ČT24", "iROZHLAS", "Root.cz"), count = 4)

        assertEquals(listOf("a", "d", "e", "b"), picked.map { it.id })
    }

    @Test
    fun `plans in topic calendars are not counted as events`() {
        val overview = TestOverviews.rich(
            events = listOf(
                TestOverviews.event("Přednáška", 9),
                TestOverviews.plan("Lekce", TestOverviews.spanish, hour = 18),
            ),
            topics = listOf(TestOverviews.school, TestOverviews.spanish),
        )

        val input = BriefingInputs.morning(overview)

        assertEquals(listOf("Přednáška"), input.events?.map { it.title })
        assertEquals(
            listOf(TopicPlanFacts("Španělština", listOf(PlanItemFacts("Lekce", "v šest večer")))),
            input.topicPlans,
        )
    }

    @Test
    fun `the evening knows which topics have nothing for tomorrow, but only when the calendar was read`() {
        val topics = listOf(TestOverviews.school, TestOverviews.spanish)

        val loaded = BriefingInputs.evening(TestOverviews.rich(tomorrowEvents = emptyList(), topics = topics))
        val failed = BriefingInputs.evening(TestOverviews.empty().copy(topics = topics))

        assertEquals(listOf("Škola", "Španělština"), loaded.topicsWithoutPlansTomorrow)
        assertEquals(emptyList<String>(), failed.topicsWithoutPlansTomorrow)
    }

    // The morning routine (M5).

    @Test
    fun `the routine reads the next chapter of the queue, not the plan's reading`() {
        val facts = BriefingInputs.routine(TestOverviews.rich().copy(routine = TestOverviews.routine()))

        assertEquals(ReadingFact.Next("Marek, třetí kapitola"), facts.reading)
        assertEquals(
            listOf("Kroužení rameny", "Rotace trupu vsedě", "Pěst a prsty", "Pohled do dálky"),
            facts.exercises,
        )
        assertEquals("Čočkový salát", facts.recipe)
    }

    @Test
    fun `the routine says the reading is done or the queue finished`() {
        fun reading(routine: RoutineStatus) =
            BriefingInputs.routine(TestOverviews.rich().copy(routine = routine)).reading

        assertEquals(ReadingFact.DoneToday, reading(TestOverviews.routine(readToday = true)))
        assertEquals(
            ReadingFact.DoneToday,
            reading(TestOverviews.routine(next = null, readToday = true, finished = true)),
        )
        assertEquals(ReadingFact.Finished, reading(TestOverviews.routine(next = null, finished = true)))
    }

    @Test
    fun `without a queue the plan's reading is used, without a valid plan nothing`() {
        val unused = TestOverviews.routine(next = null)

        val withPlan = BriefingInputs.routine(TestOverviews.rich().copy(routine = unused))
        val withoutPlan = BriefingInputs.routine(TestOverviews.empty().copy(routine = unused))

        assertEquals(ReadingFact.Next("Marek, první kapitola"), withPlan.reading)
        assertNull(withoutPlan.reading)
    }

    @Test
    fun `nothing is said about a queue that could not be read`() {
        val facts = BriefingInputs.routine(TestOverviews.rich())

        assertEquals(RoutineFacts(reading = null, exercises = emptyList(), recipe = null), facts)
    }

    @Test
    fun `the evening reminds of today's chapter only when nothing was read and not on Saturday`() {
        val tuesday = TestOverviews.rich()
        val saturday = tuesday.copy(loadedAt = tuesday.loadedAt.plusDays(4))

        assertEquals(
            "Marek, třetí kapitola",
            BriefingInputs.readingReminder(tuesday.copy(routine = TestOverviews.routine())),
        )
        assertNull(BriefingInputs.readingReminder(tuesday.copy(routine = TestOverviews.routine(readToday = true))))
        assertNull(BriefingInputs.readingReminder(tuesday.copy(routine = TestOverviews.routine(next = null))))
        assertNull(BriefingInputs.readingReminder(tuesday))
        assertNull(BriefingInputs.readingReminder(saturday.copy(routine = TestOverviews.routine())))
    }
}
