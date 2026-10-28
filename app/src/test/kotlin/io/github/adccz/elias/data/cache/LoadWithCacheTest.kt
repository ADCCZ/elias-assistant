package io.github.adccz.elias.data.cache

import io.github.adccz.elias.data.network.InvalidResponseException
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.testing.InMemorySourceCache
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadWithCacheTest {
    private val yesterday = Instant.parse("2026-10-27T05:30:00Z")
    private val now = Instant.parse("2026-10-28T05:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    /** A "source" whose answer is a number written as text. Like the real parsers, it reports broken data. */
    private fun parse(payload: ByteArray): Int =
        payload.decodeToString().toIntOrNull() ?: throw InvalidResponseException("Not a number")

    private suspend fun load(cache: SourceCache, fetch: suspend () -> ByteArray) =
        loadWithCache(cache, key = "numbers", clock = clock, fetch = fetch, parse = ::parse)

    private val offline: suspend () -> ByteArray = { throw IOException("No network") }

    @Test
    fun `returns fresh data and keeps a copy`() = runTest {
        val cache = InMemorySourceCache()

        val result = load(cache) { "42".encodeToByteArray() }

        assertEquals(SourceResult.Success(42, now), result)
        assertEquals("42", cache.saved.getValue("numbers").payload.decodeToString())
        assertEquals(now, cache.saved.getValue("numbers").loadedAt)
    }

    @Test
    fun `returns the cached copy when the network fails`() = runTest {
        val cache = InMemorySourceCache().apply { save("numbers", "7".encodeToByteArray(), yesterday) }

        val result = load(cache, offline)

        assertEquals(SourceResult.Success(7, yesterday, fromCache = true), result)
    }

    @Test
    fun `reports the failure when there is no copy`() = runTest {
        val result = load(InMemorySourceCache(), offline)

        assertEquals(FailureReason.NETWORK, (result as SourceResult.Failure).reason)
    }

    @Test
    fun `a broken answer does not replace a good copy`() = runTest {
        val cache = InMemorySourceCache().apply { save("numbers", "7".encodeToByteArray(), yesterday) }

        val result = load(cache) { "<html>maintenance</html>".encodeToByteArray() }

        assertEquals(SourceResult.Success(7, yesterday, fromCache = true), result)
        assertEquals("7", cache.saved.getValue("numbers").payload.decodeToString())
    }

    @Test
    fun `a copy that cannot be parsed is ignored`() = runTest {
        val cache = InMemorySourceCache().apply { save("numbers", "garbage".encodeToByteArray(), yesterday) }

        val result = load(cache, offline)

        assertEquals(FailureReason.NETWORK, (result as SourceResult.Failure).reason)
    }

    @Test
    fun `a broken cache never breaks the source`() = runTest {
        val cache = InMemorySourceCache(failing = true)

        val fresh = load(cache) { "42".encodeToByteArray() }
        val offlineResult = load(cache, offline)

        assertEquals(SourceResult.Success(42, now), fresh)
        assertTrue(offlineResult is SourceResult.Failure)
    }
}
