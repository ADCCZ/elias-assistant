package io.github.adccz.elias.data.news

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.data.network.createHttpClient
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.news.NewsFeed
import io.github.adccz.elias.testing.InMemorySourceCache
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RssNewsRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-27T06:00:00Z"), PRAGUE_ZONE)
    private val feed = NewsFeed(name = "Test", url = "https://example.org/rss")
    private val fixture = javaClass.getResourceAsStream("/rss/cdata-gmt-guid.xml")!!.readBytes()

    private val cache = InMemorySourceCache()

    private fun repository(engine: MockEngine) = RssNewsRepository(
        client = createHttpClient(engine, userAgent = "test", maxRetries = 0),
        parser = RssParser(),
        clock = clock,
        ioDispatcher = Dispatchers.Unconfined,
        cache = cache,
    )

    @Test
    fun `loads headlines from the feed URL`() = runTest {
        var requestedUrl: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(fixture)
        }

        val result = repository(engine).headlines(feed)

        assertEquals("https://example.org/rss", requestedUrl)
        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        assertEquals(2, (success.data as List<*>).size)
    }

    @Test
    fun `a missing feed becomes SERVER_ERROR`() = runTest {
        val result = repository(MockEngine { respondError(HttpStatusCode.NotFound) }).headlines(feed)

        assertEquals(SourceResult.Failure(FailureReason.SERVER_ERROR, "HTTP 404"), result)
    }

    @Test
    fun `an HTML error page instead of RSS becomes INVALID_DATA`() = runTest {
        val result = repository(MockEngine { respond("<html><body>Chyba<br></body>") }).headlines(feed)

        assertEquals(FailureReason.INVALID_DATA, (result as SourceResult.Failure).reason)
    }

    @Test
    fun `returns cached headlines of the same feed when the network fails`() = runTest {
        repository(MockEngine { respond(fixture) }).headlines(feed)

        val offline = repository(MockEngine { throw IOException("offline") })

        val cached = assertInstanceOf(SourceResult.Success::class.java, offline.headlines(feed))
        assertTrue(cached.fromCache)
        assertEquals(2, (cached.data as List<*>).size)
        val otherFeed = offline.headlines(NewsFeed(name = "Other", url = "https://example.org/other"))
        assertEquals(FailureReason.NETWORK, (otherFeed as SourceResult.Failure).reason)
    }
}
