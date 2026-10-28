package io.github.adccz.elias.domain.model

import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs one source with a time limit and turns an unexpected exception into a failure of that source only.
 * Without this, one crashing source would cancel the others, because coroutines started in one scope fail together.
 */
suspend fun <T> loadGuarded(timeout: Duration, block: suspend () -> SourceResult<T>): SourceResult<T> = try {
    withTimeoutOrNull(timeout) { block() }
        ?: SourceResult.Failure(FailureReason.TIMEOUT, "No answer within $timeout")
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    SourceResult.Failure(FailureReason.UNKNOWN, e.toString())
}
