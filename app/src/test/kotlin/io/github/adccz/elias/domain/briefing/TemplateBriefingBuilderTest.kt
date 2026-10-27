package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.news.Headline
import io.github.adccz.elias.testing.TestOverviews
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateBriefingBuilderTest {
    private val builder = TemplateBriefingBuilder()

    private fun morning(overview: io.github.adccz.elias.domain.overview.TodayOverview = TestOverviews.rich()) =
        builder.morning(BriefingInputs.morning(overview))

    private fun evening(overview: io.github.adccz.elias.domain.overview.TodayOverview = TestOverviews.rich()) =
        builder.evening(BriefingInputs.evening(overview))

    private fun assertSpeakable(text: String) {
        val markdown = Regex("""(^|\n)\s*([-*•#]|\d+\.)\s""")
        assertFalse(markdown.containsMatchIn(text)) { "Lists or headings are not for listening:\n$text" }
        assertFalse("**" in text || "__" in text) { "No markdown:\n$text" }
    }

    @Test
    fun `a full morning has every part in the spec's order and fits the length`() {
        val briefing = morning()

        assertEquals(
            listOf(
                SectionKind.GREETING_AND_WEATHER,
                SectionKind.CALENDAR,
                SectionKind.PLAN,
                SectionKind.NEWS,
                SectionKind.WARNINGS,
                SectionKind.CLOSING,
            ),
            briefing.sections.filter { it.text.isNotBlank() }.map { it.kind },
        )
        assertTrue(briefing.wordCount in BriefingLength.MORNING) { "${briefing.wordCount} words:\n${briefing.text}" }
        assertSpeakable(briefing.text)
    }

    @Test
    fun `the morning greets, gives the date and the weather in words`() {
        val text = morning().text

        assertTrue(text.startsWith("Dobré ráno, Oldo. Dnes je úterý sedmadvacátého října.")) { text }
        assertTrue("V Plzni bude ráno tři stupně, odpoledne čtrnáct stupňů, skoro jasno." in text) { text }
        assertTrue("Kolem třetí odpoledne hlásí slabý déšť, s pravděpodobností sedmdesát procent." in text) { text }
        assertTrue("Pozor na vítr, v nárazech až pětapadesát kilometrů za hodinu." in text) { text }
    }

    @Test
    fun `the morning reads events, plan, headlines and deadlines from the input`() {
        val text = morning().text

        assertTrue("V kalendáři máš dnes dvě události. Celý den: Státní svátek." in text) { text }
        assertTrue("Ve čtvrt na deset: Přednáška z databází, UP-105." in text) { text }
        assertTrue("Minimum dne: vstát do sedmi a jeden hlavní úkol." in text) { text }
        assertTrue("Ranní čtení: Marek, první kapitola." in text) { text }
        assertTrue("Na programu je angličtina a běh." in text) { text }
        assertTrue("Téma týdne je trpělivost: nechat druhé dokončit myšlenku." in text) { text }
        assertTrue("Ze světa: Město otevřelo novou knihovnu. Silnice mezi dvěma vesnicemi" in text) { text }
        assertTrue("Zítra máš povinný termín: Databáze, odevzdat první úkol." in text) { text }
    }

    @Test
    fun `when every source fails, it says so instead of inventing anything`() {
        val briefing = morning(TestOverviews.empty())

        assertTrue("Počasí se mi nepodařilo zjistit." in briefing.text)
        assertTrue("Kalendář se mi nepodařilo přečíst." in briefing.text)
        assertTrue("Plán se mi nepodařilo načíst." in briefing.text)
        assertTrue("Titulky se mi nepodařilo načíst." in briefing.text)
        assertTrue(briefing.wordCount <= BriefingLength.MORNING.last)
    }

    @Test
    fun `a busy day is shortened to stay within the length`() {
        val manyEvents = (8..17).map {
            TestOverviews.event("Schůzka číslo $it s dlouhým názvem", it, location = "Zasedačka")
        }
        val longHeadlines = (1..6).map {
            Headline(
                "$it",
                "Velmi dlouhý titulek zprávy číslo $it, který má spoustu slov a pokračuje dál a dál",
                "ČT24",
                null,
                Instant.EPOCH,
            )
        }

        val briefing = morning(TestOverviews.rich(events = manyEvents, headlines = longHeadlines))

        assertTrue(briefing.wordCount <= BriefingLength.MORNING.last) {
            "${briefing.wordCount} words:\n${briefing.text}"
        }
        assertTrue("V kalendáři máš dnes deset událostí." in briefing.text) { briefing.text }
        assertTrue("Zbytek najdeš v kalendáři." in briefing.text) { briefing.text }
    }

    @Test
    fun `a full evening fits the length and asks about the weekly theme`() {
        val briefing = evening()

        assertTrue(briefing.wordCount in BriefingLength.EVENING) { "${briefing.wordCount} words:\n${briefing.text}" }
        assertTrue(briefing.text.startsWith("Dobrý večer, Oldo. Než den skončí, odškrtni dnešek v checklistu")) {
            briefing.text
        }
        assertTrue(
            "Zítra je středa osmadvacátého října. V kalendáři máš jednu událost. V deset: Cvičení." in briefing.text,
        ) {
            briefing.text
        }
        assertTrue("Máš povinný termín: Databáze, odevzdat první úkol." in briefing.text) { briefing.text }
        assertTrue("Jak se ti dnes dařilo nechat druhé dokončit myšlenku?" in briefing.text) { briefing.text }
        assertTrue(briefing.text.endsWith("Teď dej telefon pryč a do půlnoci spát. Dobrou noc, Oldo.")) {
            briefing.text
        }
        assertSpeakable(briefing.text)
    }

    @Test
    fun `the plan's own reflection question wins`() {
        val withQuestion = TestOverviews.richDayPlan.copy(
            weeklyTheme = TestOverviews.richDayPlan.weeklyTheme?.copy(
                reflectionQuestion = "Komu jsi dnes skočil do řeči",
            ),
        )

        val text = evening(TestOverviews.rich(dayPlan = withQuestion)).text

        assertTrue("Komu jsi dnes skočil do řeči?" in text) { text }
        assertFalse("Jak se ti dnes dařilo" in text) { text }
    }

    @Test
    fun `an evening without data still sends you to bed`() {
        val text = evening(TestOverviews.empty()).text

        assertTrue("Kalendář na zítřek se mi nepodařilo přečíst." in text) { text }
        assertTrue(text.endsWith("Dobrou noc, Oldo.")) { text }
    }
}
