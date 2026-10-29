package io.github.adccz.elias.data.cache

import io.github.adccz.elias.data.network.loadSafely
import io.github.adccz.elias.domain.model.SourceResult
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/** The last raw answer of a network source and when it was loaded. */
class CachedPayload(val payload: ByteArray, val loadedAt: Instant)

/**
 * Keeps the last raw answer of each network source (the weather forecast, each RSS feed) on the phone.
 * It holds public data only, never anything personal. Implemented with Room.
 */
interface SourceCache {
    suspend fun save(key: String, payload: ByteArray, loadedAt: Instant)

    suspend fun load(key: String): CachedPayload?
}

/**
 * Loads a network source and keeps a copy of its raw answer. When loading fails, the last answer that worked is
 * parsed again and returned instead, marked [SourceResult.Success.fromCache], with the time it was loaded.
 *
 * Only an answer that [parse] accepted is saved, so a broken answer never replaces a good copy.
 * A cache that cannot be read or written is ignored: the source then behaves as if there were no cache.
 */
suspend fun <T> loadWithCache(
    cache: SourceCache,
    key: String,
    clock: Clock,
    fetch: suspend () -> ByteArray,
    parse: (ByteArray) -> T,
): SourceResult<T> {
    val fresh = loadSafely(clock) {
        val payload = fetch()
        payload to parse(payload)
    }
    return when (fresh) {
        is SourceResult.Success -> {
            val (payload, data) = fresh.data
            quietly { cache.save(key, payload, fresh.loadedAt) }
            SourceResult.Success(data, fresh.loadedAt)
        }

        is SourceResult.Failure -> quietly {
            cache.load(key)?.let { cached ->
                SourceResult.Success(parse(cached.payload), cached.loadedAt, fromCache = true)
            }
        } ?: fresh
    }
}

/** Runs [block] and turns any failure into null. Cancellation still goes through, so coroutines can stop. */
private suspend fun <T> quietly(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
