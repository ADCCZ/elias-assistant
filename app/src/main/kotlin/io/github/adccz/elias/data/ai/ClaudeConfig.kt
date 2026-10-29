package io.github.adccz.elias.data.ai

/**
 * Settings for the Claude API. The model will be selectable in settings (M8); until then the default is used.
 *
 * [toString] never prints the key, so the config can be logged safely.
 */
data class ClaudeConfig(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    override fun toString(): String =
        "ClaudeConfig(model=$model, apiKey=${if (apiKey.isBlank()) "<none>" else "<set>"}, timeoutMillis=$timeoutMillis)"

    companion object {
        /** The cheapest current model (checked 2026-10-27). */
        const val DEFAULT_MODEL = "claude-haiku-5-5"

        /** API version 2026-10-21 rejects temperature, top_p and top_k. */
        const val API_VERSION = "2026-10-21"
        const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"

        /** The spec's limit: after this, the template takes over. */
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
