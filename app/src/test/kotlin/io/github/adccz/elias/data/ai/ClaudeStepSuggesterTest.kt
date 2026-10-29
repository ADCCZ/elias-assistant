package io.github.adccz.elias.data.ai

import io.github.adccz.elias.data.network.createHttpClient
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.journal.StepRequest
import io.github.adccz.elias.domain.journal.StepResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** runBlocking, not runTest: the client's real-time timeout would expire at once in virtual time. */
class ClaudeStepSuggesterTest {
    private val request = StepRequest("Méně odkládat úkoly", "Trpělivost", "Nechat druhé dokončit myšlenku")

    private fun suggester(engine: MockEngine, apiKey: String = "test-key") = ClaudeStepSuggester(
        client = createHttpClient(engine, userAgent = "test", maxRetries = 0),
        config = ClaudeConfig(apiKey = apiKey),
    )

    private fun answer(text: String) = buildJsonObject {
        put("model", "claude-haiku-5-5")
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", text)
            }
        }
        put("stop_reason", "end_turn")
    }.toString()

    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `sends only the request fields and returns the step`() = runBlocking {
        var body: JsonObject? = null
        val engine = MockEngine { call ->
            body = Json.parseToJsonElement((call.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                .jsonObject
            respondJson(answer("""{"step":" Zítra začni nejtěžším úkolem hned ráno. "}"""))
        }

        val result = suggester(engine).suggest(request)

        assertEquals(StepResult.Success("Zítra začni nejtěžším úkolem hned ráno."), result)
        val sent = requireNotNull(body)
        val userText = sent["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(""""to_improve":"Méně odkládat úkoly"""" in userText) { userText }
        assertTrue(""""weekly_theme":"Trpělivost"""" in userText) { userText }
        assertEquals(ClaudePrompts.STEP_SYSTEM, sent["system"]?.jsonPrimitive?.content)
        listOf("temperature", "top_p", "top_k").forEach { assertFalse(it in sent) { "$it must not be sent" } }
    }

    @Test
    fun `without a key it does not call the API`() = runBlocking {
        var called = false
        val engine = MockEngine {
            called = true
            respondJson(answer("""{"step":"x"}"""))
        }

        val result = suggester(engine, apiKey = "").suggest(request)

        assertEquals(StepResult.Failure(AiFailureReason.NO_API_KEY), result)
        assertFalse(called)
    }

    @Test
    fun `a missing credit is reported as such`() = runBlocking {
        val engine = MockEngine {
            respondJson(
                """{"type":"error","error":{"type":"billing_error","message":"Your credit balance is too low"}}""",
                HttpStatusCode.BadRequest,
            )
        }

        val result = suggester(engine).suggest(request) as StepResult.Failure

        assertEquals(AiFailureReason.NO_CREDIT, result.reason)
    }

    @Test
    fun `a step that is too long or not JSON is not used`() = runBlocking {
        val long = List(50) { "slovo" }.joinToString(" ")

        val tooLong = suggester(MockEngine { respondJson(answer("""{"step":"$long"}""")) }).suggest(request)
        val notJson = suggester(MockEngine { respondJson(answer("Zeptej se.")) }).suggest(request)

        assertEquals(AiFailureReason.INVALID_OUTPUT, (tooLong as StepResult.Failure).reason)
        assertEquals(AiFailureReason.INVALID_OUTPUT, (notJson as StepResult.Failure).reason)
    }
}
