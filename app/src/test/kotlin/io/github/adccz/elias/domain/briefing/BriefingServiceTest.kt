package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.testing.TestOverviews
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
}
