package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.journal.JournalReminder
import io.github.adccz.elias.domain.journal.JournalStatus
import io.github.adccz.elias.testing.TestOverviews
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BriefingServiceTest {
    private val template = TemplateBriefingBuilder()
    private val overview = TestOverviews.rich()
    private val aiText = BriefingText(
        BriefingKind.MORNING,
        listOf(BriefingSection(SectionKind.GREETING_AND_WEATHER, "Text od AI.")),
    )
    private val usage = AiUsage("claude-haiku-5-5", 1_000, 400)

    /** A fake AI that returns [result] and counts how often it was asked. */
    private class FakeAi(private val result: () -> AiBriefingResult) : AiBriefingBuilder {
        var calls = 0

        override suspend fun morning(input: MorningInput): AiBriefingResult {
            calls++
            return result()
        }

        override suspend fun evening(input: EveningInput): AiBriefingResult {
            calls++
            return result()
        }
    }

    @Test
    fun `uses the AI text when the AI succeeds`() = runTest {
        val service = BriefingService(template, FakeAi { AiBriefingResult.Success(aiText, usage) })

        val result = service.morning(overview)

        assertEquals(BriefingSource.AI, result.source)
        assertEquals("Text od AI.", result.briefing.text)
        assertEquals(usage, result.usage)
        assertNull(result.aiFailure)
    }

    @Test
    fun `falls back to the template and keeps the reason`() = runTest {
        val failure = AiBriefingResult.Failure(AiFailureReason.NO_CREDIT, "HTTP 400")
        val service = BriefingService(template, FakeAi { failure })

        val result = service.evening(overview)

        assertEquals(BriefingSource.TEMPLATE, result.source)
        assertEquals(failure, result.aiFailure)
        assertEquals(template.evening(BriefingInputs.evening(overview)).text, result.briefing.text)
    }

    @Test
    fun `a crashing AI builder still gives a briefing`() = runTest {
        val service = BriefingService(template, FakeAi { error("bug") })

        val result = service.morning(overview)

        assertEquals(BriefingSource.TEMPLATE, result.source)
        assertEquals(AiFailureReason.UNKNOWN, result.aiFailure?.reason)
    }

    @Test
    fun `with AI switched off the AI is not asked at all`() = runTest {
        val ai = FakeAi { AiBriefingResult.Success(aiText, usage) }

        val result = BriefingService(template, ai).morning(overview, useAi = false)

        assertEquals(BriefingSource.TEMPLATE, result.source)
        assertEquals(AiFailureReason.DISABLED, result.aiFailure?.reason)
        assertEquals(0, ai.calls)
        assertFalse(result.briefing.text.isBlank())
    }

    // The journal (M4.5): added on the phone after the AI answer.

    private fun withJournal(writtenToday: Boolean?, reminder: JournalReminder? = null) =
        overview.copy(journal = JournalStatus(writtenToday, reminder))

    @Test
    fun `the morning adds yesterday's step after the plan, also to an AI text`() = runTest {
        val service = BriefingService(template, FakeAi { AiBriefingResult.Success(aiText, usage) })

        val result = service.morning(withJournal(true, JournalReminder("Přečti si před spaním deset stran.", true)))

        assertEquals(BriefingSource.AI, result.source)
        assertEquals(
            listOf(SectionKind.GREETING_AND_WEATHER, SectionKind.JOURNAL_STEP),
            result.briefing.spokenSections.map { it.kind },
        )
        assertEquals(
            "Včera sis dal za krok: Přečti si před spaním deset stran.",
            result.briefing.spokenSections[1].text,
        )
    }

    @Test
    fun `the journal step is spoken right after the plan`() = runTest {
        val result = BriefingService(template, FakeAi { AiBriefingResult.Failure(AiFailureReason.DISABLED) })
            .morning(withJournal(true, JournalReminder("Méně odkládat úkoly", false)))

        val kinds = result.briefing.spokenSections.map { it.kind }
        assertEquals(kinds.indexOf(SectionKind.PLAN) + 1, kinds.indexOf(SectionKind.JOURNAL_STEP))
        assertTrue(
            "Včera sis zapsal, na čem se chceš zlepšit: Méně odkládat úkoly." in result.briefing.text,
        ) { result.briefing.text }
    }

    @Test
    fun `the evening reminds of the journal only when today's entry is surely missing`() = runTest {
        val service = BriefingService(template, FakeAi { AiBriefingResult.Failure(AiFailureReason.DISABLED) })

        suspend fun kinds(writtenToday: Boolean?) =
            service.evening(withJournal(writtenToday)).briefing.spokenSections.map { it.kind }

        val missing = kinds(writtenToday = false)
        assertEquals(missing.indexOf(SectionKind.GOOD_NIGHT) - 1, missing.indexOf(SectionKind.JOURNAL_REMINDER))
        assertFalse(SectionKind.JOURNAL_REMINDER in kinds(writtenToday = true))
        assertFalse(SectionKind.JOURNAL_REMINDER in kinds(writtenToday = null))
    }

    @Test
    fun `a long journal text is not read out`() {
        val long = List(40) { "slovo" }.joinToString(" ")

        val section = template.journalStep(JournalReminder(long, isStep = false))

        assertEquals("Včera sis do deníku zapsal, na čem se chceš zlepšit. Najdeš to v aplikaci.", section.text)
    }

    // The morning routine (M5): added on the phone after the AI answer.

    @Test
    fun `the morning adds the routine after the plan, also to an AI text`() = runTest {
        val service = BriefingService(template, FakeAi { AiBriefingResult.Success(aiText, usage) })

        val result = service.morning(overview.copy(routine = TestOverviews.routine()))

        assertEquals(
            listOf(SectionKind.GREETING_AND_WEATHER, SectionKind.ROUTINE),
            result.briefing.spokenSections.map { it.kind },
        )
        assertTrue(result.briefing.spokenSections[1].text.startsWith("Ranní čtení: Marek, třetí kapitola.")) {
            result.briefing.text
        }
    }

    @Test
    fun `the routine comes between the plan and yesterday's step`() = runTest {
        val journal = withJournal(true, JournalReminder("Méně odkládat úkoly", false))
        val result = BriefingService(template, FakeAi { AiBriefingResult.Failure(AiFailureReason.DISABLED) })
            .morning(journal.copy(routine = TestOverviews.routine()))

        val kinds = result.briefing.spokenSections.map { it.kind }
        assertEquals(kinds.indexOf(SectionKind.PLAN) + 1, kinds.indexOf(SectionKind.ROUTINE))
        assertEquals(kinds.indexOf(SectionKind.ROUTINE) + 1, kinds.indexOf(SectionKind.JOURNAL_STEP))
    }

    @Test
    fun `the whole template morning with the routine stays within the length`() = runTest {
        val result = BriefingService(template, FakeAi { AiBriefingResult.Failure(AiFailureReason.DISABLED) })
            .morning(overview.copy(routine = TestOverviews.routine()))

        assertTrue(result.briefing.wordCount <= BriefingLength.MORNING.last) { result.briefing.text }
    }

    @Test
    fun `the AI never gets the morning reading`() = runTest {
        var asked: MorningInput? = null
        val ai = object : AiBriefingBuilder {
            override suspend fun morning(input: MorningInput): AiBriefingResult {
                asked = input
                return AiBriefingResult.Success(aiText, usage)
            }

            override suspend fun evening(input: EveningInput): AiBriefingResult = error("not used")
        }

        BriefingService(template, ai).morning(overview.copy(routine = TestOverviews.routine()))

        assertFalse("Marek" in asked.toString()) { asked.toString() }
    }

    @Test
    fun `the evening reminds of today's chapter before the journal`() = runTest {
        val service = BriefingService(template, FakeAi { AiBriefingResult.Failure(AiFailureReason.DISABLED) })

        val result = service.evening(withJournal(false).copy(routine = TestOverviews.routine()))
        val read = service.evening(withJournal(false).copy(routine = TestOverviews.routine(readToday = true)))

        val kinds = result.briefing.spokenSections.map { it.kind }
        assertEquals(kinds.indexOf(SectionKind.JOURNAL_REMINDER) - 1, kinds.indexOf(SectionKind.READING_REMINDER))
        assertTrue("Ještě tě čeká dnešní čtení: Marek, třetí kapitola." in result.briefing.text)
        assertFalse(SectionKind.READING_REMINDER in read.briefing.spokenSections.map { it.kind })
    }
}
