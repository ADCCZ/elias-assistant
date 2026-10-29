package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.overview.TodayOverview
import io.github.adccz.elias.domain.overview.sabbathJustEnded
import io.github.adccz.elias.domain.speech.CzechNumbers
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

enum class BriefingSource { AI, TEMPLATE }

/** A finished briefing and how it was made. */
data class BriefingResult(
    val briefing: BriefingText,
    val source: BriefingSource,
    /** Why the AI text was not used. Null when the AI wrote it. */
    val aiFailure: AiBriefingResult.Failure?,
    /** Model and tokens when the AI wrote it. */
    val usage: AiUsage?,
)

/**
 * Chooses who writes the briefing: the AI first, the template whenever the AI is off or fails.
 * "AI is an improvement, not a requirement" (spec): this never fails, because the template always works.
 *
 * Overnight messages (M6) will be inserted here, after the AI answer, so they never reach the AI. So are the journal
 * and the morning routine (M5).
 */
class BriefingService @Inject constructor(
    private val template: TemplateBriefingBuilder,
    private val ai: AiBriefingBuilder,
) {
    /**
     * The morning routine and yesterday's journal step are added here by the template, after the AI answer: the
     * journal stays on the phone, and the routine's reading and names stay exact. The template's own text leaves room
     * for the routine, the AI gets a shorter target ([BriefingLength.MORNING_AI]).
     */
    suspend fun morning(overview: TodayOverview, useAi: Boolean = true): BriefingResult {
        val input = BriefingInputs.morning(overview)
        val routine = template.routine(BriefingInputs.routine(overview))
        val templateText = template.morning(input, reservedWords = CzechNumbers.wordCount(routine.text))
        val result = choose(templateText, useAi) { ai.morning(input) }
        val step = overview.journal.reminder?.let(template::journalStep)
        return result.withSections(result.briefing.sections + listOfNotNull(routine, step))
    }

    /**
     * On Saturday after sunset the briefing opens with "the sabbath has ended" instead of the greeting and the daily
     * checklist. That part is written here by the template, after the AI answer: it is fixed text with the study.
     * The reminder to write the journal is added the same way, only when today's entry is surely missing, and so is the
     * reminder of today's chapter when nothing was read yet.
     */
    suspend fun evening(overview: TodayOverview, useAi: Boolean = true): BriefingResult {
        val input = BriefingInputs.evening(overview)
        val result = choose(template.evening(input), useAi) { ai.evening(input) }
        var sections = result.briefing.sections
        if (overview.sabbathJustEnded) {
            val sabbathEnded = template.sabbathEnded(BriefingInputs.sabbathStudy(overview))
            sections = sections.filter { it.kind != SectionKind.CHECKLIST } + sabbathEnded
        }
        BriefingInputs.readingReminder(overview)?.let { sections = sections + template.readingReminder(it) }
        if (overview.journal.writtenToday == false) sections = sections + template.journalReminder()
        return result.withSections(sections)
    }

    private fun BriefingResult.withSections(sections: List<BriefingSection>) =
        copy(briefing = briefing.copy(sections = sections))

    private suspend fun choose(
        templateText: BriefingText,
        useAi: Boolean,
        writeWithAi: suspend () -> AiBriefingResult,
    ): BriefingResult {
        if (!useAi) return templateResult(templateText, AiBriefingResult.Failure(AiFailureReason.DISABLED))
        val aiResult = try {
            writeWithAi()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The AI builder should never throw, but a bug there must not cost the user their briefing.
            AiBriefingResult.Failure(AiFailureReason.UNKNOWN, e.toString())
        }
        return when (aiResult) {
            is AiBriefingResult.Success -> BriefingResult(aiResult.briefing, BriefingSource.AI, null, aiResult.usage)
            is AiBriefingResult.Failure -> templateResult(templateText, aiResult)
        }
    }

    private fun templateResult(text: BriefingText, failure: AiBriefingResult.Failure) =
        BriefingResult(text, BriefingSource.TEMPLATE, failure, usage = null)
}
