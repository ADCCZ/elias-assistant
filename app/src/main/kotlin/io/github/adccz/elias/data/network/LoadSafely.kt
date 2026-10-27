package io.github.adccz.elias.data.network

import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/** The response arrived but its content does not make sense (for example arrays of different lengths). */
class InvalidResponseException(message: String) : Exception(message)

/**
 * Runs a network [block] and turns every expected failure into a [SourceResult.Failure].
 * This is the one place where network exceptions are translated, so sources stay short.
 */
suspend fun <T> loadSafely(clock: Clock, block: suspend () -> T): SourceResult<T> = try {
    SourceResult.Success(block(), clock.instant())
} catch (e: CancellationException) {
    // Cancellation is not an error. It must reach the caller, otherwise coroutines cannot be stopped.
    throw e
} catch (e: HttpRequestTimeoutException) {
    SourceResult.Failure(FailureReason.TIMEOUT, e.message)
} catch (e: ConnectTimeoutException) {
    SourceResult.Failure(FailureReason.TIMEOUT, e.message)
} catch (e: SocketTimeoutException) {
    SourceResult.Failure(FailureReason.TIMEOUT, e.message)
} catch (e: ResponseException) {
    SourceResult.Failure(FailureReason.SERVER_ERROR, "HTTP ${e.response.status.value}")
} catch (e: ContentConvertException) {
    SourceResult.Failure(FailureReason.INVALID_DATA, e.message)
} catch (e: SerializationException) {
    SourceResult.Failure(FailureReason.INVALID_DATA, e.message)
} catch (e: InvalidResponseException) {
    SourceResult.Failure(FailureReason.INVALID_DATA, e.message)
} catch (e: IOException) {
    SourceResult.Failure(FailureReason.NETWORK, e.message)
}
