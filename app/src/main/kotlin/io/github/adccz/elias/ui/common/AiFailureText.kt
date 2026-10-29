package io.github.adccz.elias.ui.common

import androidx.annotation.StringRes
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.briefing.AiFailureReason

/** Why the AI did not answer, as a short Czech phrase ("na účtu Anthropic chybí kredit"). */
@StringRes
fun AiFailureReason.messageRes(): Int = when (this) {
    AiFailureReason.DISABLED -> R.string.ai_failure_disabled
    AiFailureReason.NO_API_KEY -> R.string.ai_failure_no_api_key
    AiFailureReason.INVALID_API_KEY -> R.string.ai_failure_invalid_api_key
    AiFailureReason.NO_CREDIT -> R.string.ai_failure_no_credit
    AiFailureReason.RATE_LIMITED -> R.string.ai_failure_rate_limited
    AiFailureReason.OVERLOADED -> R.string.ai_failure_overloaded
    AiFailureReason.SERVER_ERROR -> R.string.ai_failure_server_error
    AiFailureReason.NETWORK -> R.string.ai_failure_network
    AiFailureReason.TIMEOUT -> R.string.ai_failure_timeout
    AiFailureReason.REFUSED -> R.string.ai_failure_refused
    AiFailureReason.TRUNCATED -> R.string.ai_failure_truncated
    AiFailureReason.INVALID_OUTPUT -> R.string.ai_failure_invalid_output
    AiFailureReason.UNKNOWN -> R.string.ai_failure_unknown
}
