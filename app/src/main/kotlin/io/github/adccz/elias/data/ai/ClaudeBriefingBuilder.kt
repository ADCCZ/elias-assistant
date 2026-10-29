package io.github.adccz.elias.data.ai

import io.github.adccz.elias.domain.briefing.AiBriefingBuilder
import io.github.adccz.elias.domain.briefing.AiBriefingResult
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.BriefingValidator
import io.github.adccz.elias.domain.briefing.EveningInput
import io.github.adccz.elias.domain.briefing.MorningInput
import io.ktor.client.HttpClient
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Writes briefings with Claude through the Messages API, called directly with Ktor ([ClaudeClient]).
 *
 * The answer is checked by [BriefingValidator]. Anything unexpected becomes a [AiBriefingResult.Failure],
 * and the caller falls back to the template. This class never throws for API or network problems.
 */
class ClaudeBriefingBuilder @Inject constructor(client: HttpClient, config: ClaudeConfig) : AiBriefingBuilder {
    private val claude = ClaudeClient(client, config)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun morning(input: MorningInput): AiBriefingResult =
        generate(ClaudePrompts.morning(input), BriefingSchemas.morning) { text ->
            json.decodeFromString<MorningSections>(text).toBriefing()
        }

    override suspend fun evening(input: EveningInput): AiBriefingResult =
        generate(ClaudePrompts.evening(input), BriefingSchemas.evening) { text ->
            json.decodeFromString<EveningSections>(text).toBriefing()
        }

    private suspend fun generate(
        userText: String,
        schema: JsonObject,
        parseSections: (String) -> BriefingText,
    ): AiBriefingResult = when (val answer = claude.ask(ClaudePrompts.SYSTEM, userText, schema, MAX_TOKENS)) {
        is ClaudeAnswer.Failed -> AiBriefingResult.Failure(answer.reason, answer.detail)

        is ClaudeAnswer.Text -> try {
            val briefing = parseSections(answer.text)
            BriefingValidator.problem(briefing)
                ?.let { AiBriefingResult.Failure(AiFailureReason.INVALID_OUTPUT, it) }
                ?: AiBriefingResult.Success(briefing, answer.usage)
        } catch (e: SerializationException) {
            AiBriefingResult.Failure(AiFailureReason.INVALID_OUTPUT, e.message)
        }
    }

    private companion object {
        /** Thinking counts toward this limit too, so it leaves room above the ~600 tokens of a briefing. */
        const val MAX_TOKENS = 3000
    }
}
