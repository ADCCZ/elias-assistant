package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.model.GeoLocation
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.overview.TodayOverview
import io.github.adccz.elias.domain.overview.TodayPlan
import io.github.adccz.elias.domain.overview.sabbathJustEnded
import io.github.adccz.elias.domain.sun.SunCalculator
import io.github.adccz.elias.testing.TestOverviews
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Saturday evening after sunset: the briefing opens with "the sabbath has ended" (spec, sabbath rules). */
class SabbathEndedTest {
    private val template = TemplateBriefingBuilder()
    private val sun = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)
    private val noAi = object : AiBriefingBuilder {
        override suspend fun morning(input: MorningInput) = AiBriefingResult.Failure(AiFailureReason.DISABLED)

        override suspend fun evening(input: EveningInput) = AiBriefingResult.Failure(AiFailureReason.DISABLED)
    }

    /** The rich test day moved to [time], with the sun of that day and a Saturday study when [study] is set. */
    private fun overviewAt(time: String, study: String? = null): TodayOverview {
        val loadedAt = LocalDateTime.parse(time).atZone(PRAGUE_ZONE)
        val date = loadedAt.toLocalDate()
        val rich = TestOverviews.rich(dayPlan = TestOverviews.richDayPlan.copy(date = date, saturdayStudy = study))
        val plan = (rich.plan as SourceResult.Success<TodayPlan>).data
        return rich.copy(
            loadedAt = loadedAt,
            sunTimes = sun.sunTimes(date),
            plan = SourceResult.Success(
                plan.copy(validFrom = date.minusDays(1), validTo = date.plusDays(1)),
                loadedAt.toInstant(),
            ),
        )
    }

    // 31 October 2026 is a Saturday. The sun sets at about a quarter to five.

    @Test
    fun `the sabbath has just ended on Saturday from sunset on`() {
        val sunset = sun.sunTimes(LocalDate.of(2026, 10, 31)).sunset!!

        assertTrue(overviewAt(sunset.toLocalDateTime().toString()).sabbathJustEnded)
        assertTrue(overviewAt("2026-10-31T22:00").sabbathJustEnded)
    }

    @Test
    fun `not before Saturday's sunset and not on other days`() {
        assertFalse(overviewAt("2026-10-31T15:00").sabbathJustEnded)
        assertFalse(overviewAt("2026-10-30T22:00").sabbathJustEnded)
        assertFalse(overviewAt("2026-11-01T22:00").sabbathJustEnded)
    }

    @Test
    fun `the opening asks about the day's study and mentions tomorrow's review`() {
        val section = template.sabbathEnded("Žalm devadesát dva")

        assertEquals(SectionKind.SABBATH_ENDED, section.kind)
        assertEquals(
            "Dobrý večer, Oldo. Sabat skončil. Dnešní studium: Žalm devadesát dva. Co si z něj odnášíš? " +
                "Zítra tě čeká revize cílů a plán na další týden.",
            section.text,
        )
    }

    @Test
    fun `without a study the question is general`() {
        assertTrue(template.sabbathEnded(null).text.contains("Co si odnášíš ze sobotního studia?"))
    }

    @Test
    fun `Saturday's evening briefing opens with the end of the sabbath instead of the checklist`() = runTest {
        val briefing = BriefingService(template, noAi).evening(overviewAt("2026-10-31T22:00", study = "Ž 92")).briefing

        val kinds = briefing.spokenSections.map { it.kind }
        assertEquals(SectionKind.SABBATH_ENDED, kinds.first())
        assertFalse(SectionKind.CHECKLIST in kinds)
        assertTrue(SectionKind.TOMORROW in kinds)
        assertTrue(briefing.spokenSections.first().text.contains("Dnešní studium: "))
    }

    @Test
    fun `the AI text gets the same opening`() = runTest {
        val aiEvening = BriefingText(
            BriefingKind.EVENING,
            listOf(
                BriefingSection(SectionKind.CHECKLIST, "Dobrý večer od AI."),
                BriefingSection(SectionKind.TOMORROW, "Zítra od AI."),
            ),
        )
        val ai = object : AiBriefingBuilder {
            override suspend fun morning(input: MorningInput) = AiBriefingResult.Failure(AiFailureReason.DISABLED)

            override suspend fun evening(input: EveningInput) =
                AiBriefingResult.Success(aiEvening, AiUsage("claude-haiku-5-5", 1, 1))
        }

        val result = BriefingService(template, ai).evening(overviewAt("2026-10-31T22:00"))

        assertEquals(BriefingSource.AI, result.source)
        assertEquals(
            listOf(SectionKind.SABBATH_ENDED, SectionKind.TOMORROW),
            result.briefing.spokenSections.map {
                it.kind
            },
        )
    }

    @Test
    fun `other evenings keep the checklist`() = runTest {
        val briefing = BriefingService(template, noAi).evening(overviewAt("2026-10-31T15:00")).briefing

        assertEquals(SectionKind.CHECKLIST, briefing.spokenSections.first().kind)
    }

    @Test
    fun `the study comes from a valid plan, spoken`() {
        assertEquals(null, BriefingInputs.sabbathStudy(overviewAt("2026-10-31T22:00")))
        assertTrue(BriefingInputs.sabbathStudy(overviewAt("2026-10-31T22:00", study = "Ž 92"))!!.startsWith("Žalm"))
    }
}
