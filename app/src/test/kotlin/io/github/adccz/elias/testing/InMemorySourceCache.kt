package io.github.adccz.elias.testing

import io.github.adccz.elias.data.cache.CachedPayload
import io.github.adccz.elias.data.cache.SourceCache
import java.time.Instant

/** A [SourceCache] in a map, for tests. [failing] makes every call throw, like a broken database. */
class InMemorySourceCache(private val failing: Boolean = false) : SourceCache {
    val saved = mutableMapOf<String, CachedPayload>()

    override suspend fun save(key: String, payload: ByteArray, loadedAt: Instant) {
        if (failing) throw IllegalStateException("The database is broken")
        saved[key] = CachedPayload(payload, loadedAt)
    }

    override suspend fun load(key: String): CachedPayload? {
        if (failing) throw IllegalStateException("The database is broken")
        return saved[key]
    }
}
