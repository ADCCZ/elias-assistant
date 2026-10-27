package io.github.adccz.elias.data.ai

import io.github.adccz.elias.data.network.createHttpClient
import io.github.adccz.elias.domain.briefing.AiBriefingResult
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingInputs
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.briefing.TemplateBriefingBuilder
import io.github.adccz.elias.testing.TestOverviews
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Uses runBlocking (real time) instead of runTest: the builder's 15 s timeout would otherwise expire
 * instantly in runTest's virtual time while MockEngine answers in real time.
 */
class ClaudeBriefingBuilderTest {
    private val morningInput = BriefingInputs.morning(TestOverviews.rich())

    /** A realistic answer: the template's own sections, sent back as the model's JSON. */
    private val goodMorning: BriefingText = TemplateBriefingBuilder().morning(morningInput)

    private fun builder(engine: MockEngine, apiKey: String = "test-key", timeoutMillis: Long = 15_000) =
        ClaudeBriefingBuilder(
            client = createHttpClient(engine, userAgent = "test", maxRetries = 0),
            config = ClaudeConfig(apiKey = apiKey, timeoutMillis = timeoutMillis),
        )

    private fun morningSectionsJson(briefing: BriefingText): String = buildJsonObject {
        fun text(kind: SectionKind) = briefing.sections.firstOrNull { it.kind == kind }?.text.orEmpty()
        put("greeting_and_weather", text(SectionKind.GREETING_AND_WEATHER))
        put("calendar", text(SectionKind.CALENDAR))
        put("plan", text(SectionKind.PLAN))
        put("news", text(SectionKind.NEWS))
        put("warnings", text(SectionKind.WARNINGS))
        put("closing", text(SectionKind.CLOSING))
    }.toString()

    /** A Messages API response. It starts with an empty thinking block, as Claude Haiku 5.5 may send. */
    private fun messagesResponse(text: String, stopReason: String = "end_turn") = buildJsonObject {
        put("id", "msg_test")
        put("type", "message")
        put("role", "assistant")
        put("model", "claude-haiku-5-5")
        putJsonArray("content") {
            addJsonObject {
                put("type", "thinking")
                put("thinking", "")
                put("signature", "sig")
            }
            addJsonObject {
                put("type", "text")
                put("text", text)
            }
        }
        put("stop_reason", stopReason)
        putJsonObject("usage") {
            put("input_tokens", 1_200)
            put("output_tokens", 450)
        }
    }.toString()

    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun apiError(type: String, message: String) =
        """{"type":"error","error":{"type":"$type","message":"$message"},"request_id":"req_test"}"""

    private suspend fun morningWith(engine: MockEngine) = builder(engine).morning(morningInput)

    @Test
    fun `sends the documented headers and body, without sampling parameters`() = runBlocking {
        var headers: io.ktor.http.Headers? = null
        var body: JsonObject? = null
        val engine = MockEngine { request ->
            headers = request.headers
            body =
                Json.parseToJsonElement(
                    (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                ).jsonObject
            respondJson(messagesResponse(morningSectionsJson(goodMorning)))
        }

        morningWith(engine)

        val sent = requireNotNull(body)
        assertEquals("test-key", headers?.get("x-api-key"))
        assertEquals("2026-10-21", headers?.get("anthropic-version"))
        assertEquals("claude-haiku-5-5", sent["model"]?.jsonPrimitive?.content)
        assertEquals("low", sent["output_config"]?.jsonObject?.get("effort")?.jsonPrimitive?.content)
        assertEquals(
            "json_schema",
            sent["output_config"]?.jsonObject?.get("format")?.jsonObject?.get("type")?.jsonPrimitive?.content,
        )
        listOf("temperature", "top_p", "top_k").forEach { assertFalse(it in sent) { "$it must not be sent" } }
        val userText = sent["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue("úterý sedmadvacátého října" in userText) { "The spoken date goes to the model" }
    }

    @Test
    fun `reads the text block after a thinking block and returns the sections`() = runBlocking {
        val result = morningWith(MockEngine { respondJson(messagesResponse(morningSectionsJson(goodMorning))) })

        val success = assertInstanceOf(AiBriefingResult.Success::class.java, result)
        assertEquals(goodMorning.text, success.briefing.text)
        assertEquals(1_200, success.usage.inputTokens)
        assertEquals("claude-haiku-5-5", success.usage.model)
    }

    @Test
    fun `without a key it does not call the API at all`() = runBlocking {
        var called = false
        val engine = MockEngine {
            called = true
            respondJson("{}")
        }

        val result = builder(engine, apiKey = "").morning(morningInput)

        assertEquals(AiBriefingResult.Failure(AiFailureReason.NO_API_KEY), result)
        assertFalse(called)
    }

    @Test
    fun `maps API errors to clear reasons`() = runBlocking {
        suspend fun reasonFor(status: HttpStatusCode, body: String) =
            (morningWith(MockEngine { respondJson(body, status) }) as AiBriefingResult.Failure).reason

        assertEquals(
            AiFailureReason.INVALID_API_KEY,
            reasonFor(HttpStatusCode.Unauthorized, apiError("authentication_error", "invalid x-api-key")),
        )
        assertEquals(
            AiFailureReason.NO_CREDIT,
            reasonFor(
                HttpStatusCode.BadRequest,
                apiError("invalid_request_error", "Your credit balance is too low to access the Anthropic API."),
            ),
        )
        assertEquals(
            AiFailureReason.RATE_LIMITED,
            reasonFor(HttpStatusCode.TooManyRequests, apiError("rate_limit_error", "slow down")),
        )
        assertEquals(
            AiFailureReason.OVERLOADED,
            reasonFor(HttpStatusCode(529, "Overloaded"), apiError("overloaded_error", "Overloaded")),
        )
        assertEquals(
            AiFailureReason.SERVER_ERROR,
            reasonFor(HttpStatusCode.InternalServerError, apiError("api_error", "oops")),
        )
    }

    @Test
    fun `the failure detail keeps the API message but never the key`() = runBlocking {
        val result = builder(
            MockEngine {
                respondJson(apiError("authentication_error", "invalid x-api-key"), HttpStatusCode.Unauthorized)
            },
            apiKey = "sk-secret-value",
        ).morning(morningInput) as AiBriefingResult.Failure

        assertEquals("HTTP 401 authentication_error: invalid x-api-key", result.detail)
        assertFalse(result.detail.orEmpty().contains("sk-secret-value"))
    }

    @Test
    fun `a refusal or a cut-off answer is not used`() = runBlocking {
        val refused = morningWith(MockEngine { respondJson(messagesResponse("", stopReason = "refusal")) })
        val truncated =
            morningWith(MockEngine { respondJson(messagesResponse("{\"greeting", stopReason = "max_tokens")) })

        assertEquals(AiFailureReason.REFUSED, (refused as AiBriefingResult.Failure).reason)
        assertEquals(AiFailureReason.TRUNCATED, (truncated as AiBriefingResult.Failure).reason)
    }

    @Test
    fun `output that is not JSON, too short or not for listening is rejected`() = runBlocking {
        val notJson = morningWith(MockEngine { respondJson(messagesResponse("Dobré ráno, Oldo.")) })
        val tooShort = morningWith(
            MockEngine {
                respondJson(
                    messagesResponse(morningSectionsJson(BriefingText(goodMorning.kind, goodMorning.sections.take(1)))),
                )
            },
        )
        val withList = morningWith(
            MockEngine {
                val listed = goodMorning.copy(
                    sections = goodMorning.sections.map {
                        if (it.kind ==
                            SectionKind.NEWS
                        ) {
                            it.copy(text = "Ze světa:\n- první zpráva\n- druhá zpráva")
                        } else {
                            it
                        }
                    },
                )
                respondJson(messagesResponse(morningSectionsJson(listed)))
            },
        )

        listOf(notJson, tooShort, withList).forEach {
            assertEquals(AiFailureReason.INVALID_OUTPUT, (it as AiBriefingResult.Failure).reason) { it.toString() }
        }
    }

    @Test
    fun `no connection and slow answers fall back`() = runBlocking {
        val offline = morningWith(MockEngine { throw IOException("offline") })
        val slow = builder(
            MockEngine {
                delay(2_000)
                respondJson(messagesResponse(morningSectionsJson(goodMorning)))
            },
            timeoutMillis = 100,
        ).morning(morningInput)

        assertEquals(AiFailureReason.NETWORK, (offline as AiBriefingResult.Failure).reason)
        assertEquals(AiFailureReason.TIMEOUT, (slow as AiBriefingResult.Failure).reason)
    }

    @Test
    fun `the config never prints the key`() {
        assertFalse("sk-secret-value" in ClaudeConfig(apiKey = "sk-secret-value").toString())
    }

    @Test
    fun `the schemas forbid extra properties as structured outputs require`() {
        listOf(BriefingSchemas.morning, BriefingSchemas.evening).forEach { schema ->
            assertEquals("false", schema["additionalProperties"]?.jsonPrimitive?.content)
            assertEquals(
                schema["properties"]!!.jsonObject.keys,
                schema["required"]!!.jsonArray.map {
                    it.jsonPrimitive.content
                }.toSet(),
            )
        }
        // keep the "add" import used by the JSON DSL
        buildJsonObject { putJsonArray("x") { add("y") } }
    }
}
