package io.github.adccz.elias.data.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackTrigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BriefingRequestCodecTest {
    @Test
    fun `a scheduled briefing from today's data survives the trip`() {
        val request = BriefingRequest.FromToday(BriefingKind.EVENING, PlaybackTrigger.SCHEDULE)

        assertEquals(request, BriefingRequestCodec.decode(BriefingRequestCodec.encode(request)))
    }

    @Test
    fun `a written briefing keeps its sections and text exactly`() {
        val request = BriefingRequest.Written(
            BriefingText(
                BriefingKind.MORNING,
                listOf(
                    BriefingSection(SectionKind.GREETING_AND_WEATHER, "Dobré ráno, Oldo. \"Uvozovky\" a čárky, vše."),
                    BriefingSection(SectionKind.NEWS, "Druhý odstavec.\nS novým řádkem."),
                ),
            ),
        )

        assertEquals(request, BriefingRequestCodec.decode(BriefingRequestCodec.encode(request)))
    }

    @Test
    fun `missing or broken text is no request`() {
        assertNull(BriefingRequestCodec.decode(null))
        assertNull(BriefingRequestCodec.decode("{ not json"))
    }

    @Test
    fun `names this version does not know are no request`() {
        assertNull(BriefingRequestCodec.decode("""{"kind":"NIGHT","trigger":"USER"}"""))
        assertNull(BriefingRequestCodec.decode("""{"kind":"MORNING","trigger":"WIDGET"}"""))
        assertNull(
            BriefingRequestCodec.decode("""{"kind":"MORNING","trigger":"USER","sections":[{"kind":"X","text":"a"}]}"""),
        )
    }

    @Test
    fun `Cvič se mnou survives the trip`() {
        assertEquals(
            BriefingRequest.Exercises,
            BriefingRequestCodec.decode(BriefingRequestCodec.encode(BriefingRequest.Exercises)),
        )
    }
}
