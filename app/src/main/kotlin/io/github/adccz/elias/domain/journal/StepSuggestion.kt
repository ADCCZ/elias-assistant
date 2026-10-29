package io.github.adccz.elias.domain.journal

import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.plan.PlanRepository
import io.github.adccz.elias.domain.plan.dayPlan
import io.github.adccz.elias.domain.speech.CzechNumbers
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Everything that leaves the phone when the user asks for a step: what they want to improve and the weekly theme.
 * The thought of the day and what stuck with them are not part of it (spec, M4.5).
 */
data class StepRequest(val toImprove: String, val themeTitle: String?, val themeTask: String?)

sealed interface StepResult {
    data class Success(val step: String) : StepResult

    data class Failure(val reason: AiFailureReason, val detail: String? = null) : StepResult
}

/** Asks an AI model for one small step. Implemented with Claude in the data layer. */
interface StepSuggester {
    suspend fun suggest(request: StepRequest): StepResult
}

/** Checks that a suggested step is one short sentence that can be read out and written down as it is. */
object StepValidator {
    const val MAX_WORDS = 40

    private val leftovers = listOf("**", "__", "{", "}", "[", "]", "```", "#")

    /** Null when the step is fine, otherwise a short technical reason. */
    fun problem(step: String): String? {
        val words = CzechNumbers.wordCount(step)
        return when {
            step.isBlank() -> "The step is empty"
            words > MAX_WORDS -> "$words words, at most $MAX_WORDS"
            '\n' in step.trim() -> "More than one line"
            leftovers.any { it in step } -> "Contains markup or JSON leftovers"
            else -> null
        }
    }
}

/** Asks for a step towards what the user wants to improve, with the weekly theme of that day when there is one. */
class SuggestStep @Inject constructor(private val suggester: StepSuggester, private val plans: PlanRepository) {
    suspend operator fun invoke(date: LocalDate, toImprove: String): StepResult {
        val text = toImprove.trim()
        if (text.isEmpty()) return StepResult.Failure(AiFailureReason.DISABLED, "Nothing to improve was written")
        val theme = weeklyTheme(date)
        return try {
            suggester.suggest(StepRequest(text, theme?.title, theme?.task))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The suggester should never throw, but a bug there must not crash the journal.
            StepResult.Failure(AiFailureReason.UNKNOWN, e.toString())
        }
    }

    /** The user's own weekly theme. The fictional sample plan's theme is not theirs, so it is left out. */
    private suspend fun weeklyTheme(date: LocalDate) = try {
        (plans.loadPlan() as? SourceResult.Success)?.data
            ?.takeUnless { it.isSample }
            ?.plan?.dayPlan(date)?.weeklyTheme
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
