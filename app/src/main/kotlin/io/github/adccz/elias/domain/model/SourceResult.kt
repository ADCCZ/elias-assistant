package io.github.adccz.elias.domain.model

import java.time.Instant

/**
 * What a data source returns: either data or the reason it failed.
 * Sources never throw to their callers, so one failing source cannot break the briefing.
 */
sealed interface SourceResult<out T> {
    /**
     * [loadedAt] is when the data came from the source. [fromCache] is true when the source failed just now and
     * this is the last answer that worked, kept on the phone (weather and headlines only).
     */
    data class Success<T>(val data: T, val loadedAt: Instant, val fromCache: Boolean = false) : SourceResult<T>

    /** [detail] is a technical description for logs, never shown or spoken as is. */
    data class Failure(val reason: FailureReason, val detail: String? = null) : SourceResult<Nothing>
}

enum class FailureReason {
    /** The user has not granted the Android permission the source needs. */
    NO_PERMISSION,

    /** No network connection, or the server could not be reached. */
    NETWORK,

    /** The source did not answer in time. */
    TIMEOUT,

    /** The server answered with an error status. */
    SERVER_ERROR,

    /** The data arrived but could not be read (malformed JSON, XML or plan file). */
    INVALID_DATA,

    /** Anything else. */
    UNKNOWN,
}
