package io.github.adccz.elias.domain.briefing

import io.github.adccz.elias.domain.overview.TodayOverview
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
 * Overnight messages (M5) will be inserted here, after the AI answer, so they never reach the AI.
 */
class BriefingService @Inject constructor(
    private val template: TemplateBriefingBuilder,
    private val ai: AiBriefingBuilder,
) {
    suspend fun morning(overview: TodayOverview, useAi: Boolean = true): BriefingResult {
        val input = BriefingInputs.morning(overview)
        return choose(template.morning(input), useAi) { ai.morning(input) }
    }

    suspend fun evening(overview: TodayOverview, useAi: Boolean = true): BriefingResult {
        val input = BriefingInputs.evening(overview)
        return choose(template.evening(input), useAi) { ai.evening(input) }
    }

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
