package io.github.adccz.elias.data.ai

import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.journal.StepRequest
import io.github.adccz.elias.domain.journal.StepResult
import io.github.adccz.elias.domain.journal.StepSuggester
import io.github.adccz.elias.domain.journal.StepValidator
import io.ktor.client.HttpClient
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Suggests one small step for the next day with Claude. It sends only [StepRequest]: what the user wants to improve
 * and the weekly theme. The answer is checked by [StepValidator]. It never throws for API or network problems.
 */
class ClaudeStepSuggester @Inject constructor(client: HttpClient, config: ClaudeConfig) : StepSuggester {
    private val claude = ClaudeClient(client, config)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun suggest(request: StepRequest): StepResult {
        val answer = claude.ask(ClaudePrompts.STEP_SYSTEM, ClaudePrompts.step(request), StepSchema.json, MAX_TOKENS)
        return when (answer) {
            is ClaudeAnswer.Failed -> StepResult.Failure(answer.reason, answer.detail)

            is ClaudeAnswer.Text -> try {
                val step = json.decodeFromString<StepAnswer>(answer.text).step.trim()
                StepValidator.problem(step)?.let { StepResult.Failure(AiFailureReason.INVALID_OUTPUT, it) }
                    ?: StepResult.Success(step)
            } catch (e: SerializationException) {
                StepResult.Failure(AiFailureReason.INVALID_OUTPUT, e.message)
            }
        }
    }

    private companion object {
        /** One sentence needs few tokens, thinking may need more. */
        const val MAX_TOKENS = 1000
    }
}

/** What the model receives: exactly the [StepRequest]. */
@Serializable
internal data class StepData(
    @SerialName("to_improve") val toImprove: String,
    @SerialName("weekly_theme") val weeklyTheme: String?,
    @SerialName("weekly_task") val weeklyTask: String?,
)

/** What the model must return. */
@Serializable
internal data class StepAnswer(val step: String)

internal object StepSchema {
    val json: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("step") { put("type", "string") } }
        putJsonArray("required") { add("step") }
        put("additionalProperties", false)
    }
}
