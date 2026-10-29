package io.github.adccz.elias.data.ai

import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.AiUsage
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

/** The model's answer: its text (JSON by the schema), or why there is none. */
internal sealed interface ClaudeAnswer {
    data class Text(val text: String, val usage: AiUsage) : ClaudeAnswer

    /** [detail] is technical (status code, API message). It never contains the API key. */
    data class Failed(val reason: AiFailureReason, val detail: String? = null) : ClaudeAnswer
}

/**
 * One structured request to the Messages API (`POST /v1/messages`), shared by everything Elias asks Claude.
 *
 * - Effort `low` and structured outputs instead of a temperature (removed in API 2026-10-21).
 * - Every API or network problem becomes a [ClaudeAnswer.Failed]. This class never throws for them.
 */
internal class ClaudeClient(private val client: HttpClient, private val config: ClaudeConfig) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(system: String, userText: String, schema: JsonObject, maxTokens: Int): ClaudeAnswer {
        if (config.apiKey.isBlank()) return ClaudeAnswer.Failed(AiFailureReason.NO_API_KEY)
        return try {
            withTimeoutOrNull(config.timeoutMillis) { request(system, userText, schema, maxTokens) }
                ?: ClaudeAnswer.Failed(AiFailureReason.TIMEOUT, "No answer within ${config.timeoutMillis} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            failureFor(e)
        } catch (e: HttpRequestTimeoutException) {
            ClaudeAnswer.Failed(AiFailureReason.TIMEOUT, e.message)
        } catch (e: ConnectTimeoutException) {
            ClaudeAnswer.Failed(AiFailureReason.TIMEOUT, e.message)
        } catch (e: SocketTimeoutException) {
            ClaudeAnswer.Failed(AiFailureReason.TIMEOUT, e.message)
        } catch (e: IOException) {
            ClaudeAnswer.Failed(AiFailureReason.NETWORK, e.message)
        } catch (e: ContentConvertException) {
            ClaudeAnswer.Failed(AiFailureReason.INVALID_OUTPUT, e.message)
        } catch (e: SerializationException) {
            ClaudeAnswer.Failed(AiFailureReason.INVALID_OUTPUT, e.message)
        }
    }

    private suspend fun request(system: String, userText: String, schema: JsonObject, maxTokens: Int): ClaudeAnswer {
        val response = client.post(ClaudeConfig.MESSAGES_URL) {
            header("x-api-key", config.apiKey)
            header("anthropic-version", ClaudeConfig.API_VERSION)
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = config.timeoutMillis }
            setBody(requestBody(system, userText, schema, maxTokens))
        }.body<MessagesResponse>()

        // Read the stop reason before the content: a refusal or a cut-off answer is not usable.
        when (response.stopReason) {
            "refusal" -> return ClaudeAnswer.Failed(AiFailureReason.REFUSED, "stop_reason refusal")
            "max_tokens" -> return ClaudeAnswer.Failed(AiFailureReason.TRUNCATED, "stop_reason max_tokens")
        }
        // Content blocks are read by type: Haiku 5.5 may start with "thinking" blocks.
        val text = response.content.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() }
        if (text.isBlank()) return ClaudeAnswer.Failed(AiFailureReason.INVALID_OUTPUT, "No text block")
        return ClaudeAnswer.Text(
            text,
            AiUsage(
                model = response.model ?: config.model,
                inputTokens = response.usage?.inputTokens ?: 0,
                outputTokens = response.usage?.outputTokens ?: 0,
            ),
        )
    }

    private fun requestBody(system: String, userText: String, schema: JsonObject, maxTokens: Int) = buildJsonObject {
        put("model", config.model)
        put("max_tokens", maxTokens)
        put("system", system)
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

    private suspend fun failureFor(e: ResponseException): ClaudeAnswer.Failed {
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
        return ClaudeAnswer.Failed(reason, detail)
    }

    private companion object {
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
