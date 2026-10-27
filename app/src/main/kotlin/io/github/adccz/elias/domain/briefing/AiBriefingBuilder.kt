package io.github.adccz.elias.domain.briefing

/** Writes briefings with an AI model. Implemented in the data layer (Claude API). */
interface AiBriefingBuilder {
    suspend fun morning(input: MorningInput): AiBriefingResult

    suspend fun evening(input: EveningInput): AiBriefingResult
}

sealed interface AiBriefingResult {
    data class Success(val briefing: BriefingText, val usage: AiUsage) : AiBriefingResult

    /** [detail] is technical (status code, API message) and is never spoken. It never contains the API key. */
    data class Failure(val reason: AiFailureReason, val detail: String? = null) : AiBriefingResult
}

/** Which model wrote the text and how many tokens it cost. */
data class AiUsage(val model: String, val inputTokens: Int, val outputTokens: Int)

/** Why the AI did not write the briefing. Each one makes Elias fall back to the template. */
enum class AiFailureReason {
    /** AI switched off for this request (for example "template only" on the preview screen). */
    DISABLED,

    /** No key in local.properties. */
    NO_API_KEY,

    /** The key was rejected (401/403). */
    INVALID_API_KEY,

    /** The account has no credit left. */
    NO_CREDIT,

    /** Too many requests (429). */
    RATE_LIMITED,

    /** The API is temporarily overloaded (529). */
    OVERLOADED,

    /** Another server error (5xx). */
    SERVER_ERROR,

    NETWORK,

    TIMEOUT,

    /** The model declined the request (stop reason "refusal"). */
    REFUSED,

    /** The answer was cut off by the token limit. */
    TRUNCATED,

    /** The answer was not valid JSON, was too short or long, or was not written for listening. */
    INVALID_OUTPUT,

    UNKNOWN,
}
