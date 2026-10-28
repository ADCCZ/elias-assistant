package io.github.adccz.elias.domain.briefing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BriefingTextTest {
    private val briefing = BriefingText(
        BriefingKind.MORNING,
        listOf(
            BriefingSection(SectionKind.NEWS, "Ze světa: nic nového."),
            BriefingSection(SectionKind.OVERNIGHT_MESSAGES, "   "),
            BriefingSection(SectionKind.GREETING_AND_WEATHER, "  Dobré ráno, Oldo. "),
            BriefingSection(SectionKind.CALENDAR, "V kalendáři dnes nic nemáš."),
        ),
    )

    @Test
    fun `spoken sections follow the speaking order and skip empty ones`() {
        assertEquals(
            listOf(SectionKind.GREETING_AND_WEATHER, SectionKind.CALENDAR, SectionKind.NEWS),
            briefing.spokenSections.map { it.kind },
        )
    }

    @Test
    fun `spoken sections are trimmed`() {
        assertEquals("Dobré ráno, Oldo.", briefing.spokenSections.first().text)
    }

    @Test
    fun `text joins the spoken sections with an empty line`() {
        assertEquals(
            "Dobré ráno, Oldo.\n\nV kalendáři dnes nic nemáš.\n\nZe světa: nic nového.",
            briefing.text,
        )
    }
}
