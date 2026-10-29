package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.domain.news.pickForBriefing
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
        assertEquals("Marek, první kapitola", input.plan?.morningReading)
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
}
