package io.github.adccz.elias.data.ai

import io.github.adccz.elias.domain.briefing.AiBriefingBuilder
import io.github.adccz.elias.domain.briefing.AiBriefingResult
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.AiUsage
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.BriefingValidator
import io.github.adccz.elias.domain.briefing.EveningInput
import io.github.adccz.elias.domain.briefing.MorningInput
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Writes briefings with Claude through the Messages API (`POST /v1/messages`), called directly with Ktor.
 *
 * - Effort `low` and structured outputs instead of a temperature (removed in API 2026-10-21).
 * - The answer is checked by [BriefingValidator]. Anything unexpected becomes a [AiBriefingResult.Failure],
 *   and the caller falls back to the template. This class never throws for API or network problems.
 */
class ClaudeBriefingBuilder @Inject constructor(private val client: HttpClient, private val config: ClaudeConfig) :
    AiBriefingBuilder {
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
    ): AiBriefingResult {
        if (config.apiKey.isBlank()) return AiBriefingResult.Failure(AiFailureReason.NO_API_KEY)
        return try {
            withTimeoutOrNull(config.timeoutMillis) { requestAndCheck(userText, schema, parseSections) }
                ?: AiBriefingResult.Failure(AiFailureReason.TIMEOUT, "No answer within ${config.timeoutMillis} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            failureFor(e)
        } catch (e: HttpRequestTimeoutException) {
            AiBriefingResult.Failure(AiFailureReason.TIMEOUT, e.message)
        } catch (e: ConnectTimeoutException) {
            AiBriefingResult.Failure(AiFailureReason.TIMEOUT, e.message)
        } catch (e: SocketTimeoutException) {
            AiBriefingResult.Failure(AiFailureReason.TIMEOUT, e.message)
        } catch (e: IOException) {
            AiBriefingResult.Failure(AiFailureReason.NETWORK, e.message)
        } catch (e: ContentConvertException) {
            AiBriefingResult.Failure(AiFailureReason.INVALID_OUTPUT, e.message)
        } catch (e: SerializationException) {
            AiBriefingResult.Failure(AiFailureReason.INVALID_OUTPUT, e.message)
        }
    }

    private suspend fun requestAndCheck(
        userText: String,
        schema: JsonObject,
        parseSections: (String) -> BriefingText,
    ): AiBriefingResult {
        val response = client.post(ClaudeConfig.MESSAGES_URL) {
            header("x-api-key", config.apiKey)
            header("anthropic-version", ClaudeConfig.API_VERSION)
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = config.timeoutMillis }
            setBody(requestBody(userText, schema))
        }.body<MessagesResponse>()

        // Read the stop reason before the content: a refusal or a cut-off answer is not a briefing.
        when (response.stopReason) {
            "refusal" -> return AiBriefingResult.Failure(AiFailureReason.REFUSED, "stop_reason refusal")
            "max_tokens" -> return AiBriefingResult.Failure(AiFailureReason.TRUNCATED, "stop_reason max_tokens")
        }
        // Content blocks are read by type: Haiku 5.5 may start with "thinking" blocks.
        val text = response.content.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() }
        if (text.isBlank()) return AiBriefingResult.Failure(AiFailureReason.INVALID_OUTPUT, "No text block")

        val briefing = parseSections(text)
        BriefingValidator.problem(briefing)?.let { return AiBriefingResult.Failure(AiFailureReason.INVALID_OUTPUT, it) }
        return AiBriefingResult.Success(
            briefing,
            AiUsage(
                model = response.model ?: config.model,
                inputTokens = response.usage?.inputTokens ?: 0,
                outputTokens = response.usage?.outputTokens ?: 0,
            ),
        )
    }

    private fun requestBody(userText: String, schema: JsonObject) = buildJsonObject {
        put("model", config.model)
        // Thinking counts toward this limit too, so it leaves room above the ~600 tokens of a briefing.
        put("max_tokens", MAX_TOKENS)
        put("system", ClaudePrompts.SYSTEM)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                put("content", userText)
            }
        }
        putJsonObject("output_config") {
            put("effort", "low")
            putJsonObject("format") {
                put("type", "json_schema")
                put("schema", schema)
            }
        }
    }

    private suspend fun failureFor(e: ResponseException): AiBriefingResult.Failure {
        val status = e.response.status.value
        val body = runCatching { e.response.bodyAsText() }.getOrNull().orEmpty()
        val error = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
        val type = error?.get("type")?.jsonPrimitive?.content
        val message = error?.get("message")?.jsonPrimitive?.content.orEmpty()
        val detail = "HTTP $status ${type.orEmpty()}: $message".take(MAX_DETAIL_LENGTH)
        val reason = when {
            status == 402 || type == "billing_error" || "credit balance" in message -> AiFailureReason.NO_CREDIT
            status == 401 || status == 403 -> AiFailureReason.INVALID_API_KEY
            status == 429 -> AiFailureReason.RATE_LIMITED
            status == 529 -> AiFailureReason.OVERLOADED
            status >= 500 -> AiFailureReason.SERVER_ERROR
            else -> AiFailureReason.UNKNOWN
        }
        return AiBriefingResult.Failure(reason, detail)
    }

    private companion object {
        const val MAX_TOKENS = 3000
        const val MAX_DETAIL_LENGTH = 200
    }
}

@Serializable
internal data class MessagesResponse(
    val model: String? = null,
    val content: List<ContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: Usage? = null,
) {
    @Serializable
    data class ContentBlock(val type: String, val text: String? = null)

    @Serializable
    data class Usage(
        @SerialName("input_tokens") val inputTokens: Int = 0,
        @SerialName("output_tokens") val outputTokens: Int = 0,
    )
}
