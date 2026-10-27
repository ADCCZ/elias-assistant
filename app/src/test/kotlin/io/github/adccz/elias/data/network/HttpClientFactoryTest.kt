package io.github.adccz.elias.data.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpClientFactoryTest {
    @Test
    fun `retries after a server error and sends the user agent`() = runTest {
        var attempts = 0
        var userAgent: String? = null
        val engine = MockEngine { request ->
            attempts++
            userAgent = request.headers["User-Agent"]
            if (attempts == 1) respondError(HttpStatusCode.ServiceUnavailable) else respond("ok")
        }
        val client = createHttpClient(engine, userAgent = "Elias/test", maxRetries = 1)

        val body = client.get("https://example.org").bodyAsText()

        assertEquals("ok", body)
        assertEquals(2, attempts)
        assertEquals("Elias/test", userAgent)
    }
}
