package io.github.adccz.elias.data.ai

import io.github.adccz.elias.domain.briefing.BriefingInputs
import io.github.adccz.elias.testing.TestOverviews
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClaudePromptsTest {
    private val topics = listOf(TestOverviews.school, TestOverviews.spanish)

    @Test
    fun `the morning data has the plans by topic, without a time for all-day plans`() {
        val overview = TestOverviews.rich(
            events = listOf(TestOverviews.plan("Připravit referát", TestOverviews.school)),
            topics = topics,
        )

        val prompt = ClaudePrompts.morning(BriefingInputs.morning(overview))

        assertTrue("\"topic_plans\":[{\"topic\":\"Škola\",\"plans\":[{\"title\":\"Připravit referát\"}]}]" in prompt) {
            prompt
        }
        assertTrue("\"events\":[]" in prompt) { prompt }
    }

    @Test
    fun `the evening data names the topics without a plan for tomorrow`() {
        val overview = TestOverviews.rich(tomorrowEvents = emptyList(), topics = topics)

        val prompt = ClaudePrompts.evening(BriefingInputs.evening(overview))

        assertTrue("\"topics_without_plans\":[\"Škola\",\"Španělština\"]" in prompt) { prompt }
    }
}
