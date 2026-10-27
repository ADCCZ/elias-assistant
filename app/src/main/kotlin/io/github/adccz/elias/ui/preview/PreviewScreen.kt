package io.github.adccz.elias.ui.preview

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.briefing.AiBriefingResult
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingResult
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.ui.theme.EliasTheme
import kotlin.math.roundToInt

@Composable
fun PreviewRoute(onBack: () -> Unit, viewModel: PreviewViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PreviewScreen(
        uiState = uiState,
        onBack = onBack,
        onSelect = viewModel::select,
        onUseAiChange = viewModel::setUseAi,
        onRegenerate = viewModel::generate,
    )
}

/** Shows the morning or evening text, who wrote it and why, and how long it takes to say. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    uiState: PreviewUiState,
    onBack: () -> Unit,
    onSelect: (BriefingKind) -> Unit,
    onUseAiChange: (Boolean) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRegenerate, enabled = !uiState.isLoading) {
                        Text(stringResource(R.string.preview_regenerate))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindSelector(selected = uiState.selected, onSelect = onSelect)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.preview_use_ai), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Switch(checked = uiState.useAi, onCheckedChange = onUseAiChange, enabled = !uiState.isLoading)
            }
            if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val result = uiState.current
            if (result == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                BriefingCard(result)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSelector(selected: BriefingKind, onSelect: (BriefingKind) -> Unit) {
    val kinds = listOf(
        BriefingKind.MORNING to R.string.preview_morning,
        BriefingKind.EVENING to R.string.preview_evening,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        kinds.forEachIndexed { index, (kind, label) ->
            SegmentedButton(
                selected = selected == kind,
                onClick = { onSelect(kind) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun BriefingCard(result: BriefingResult) {
    val words = result.briefing.wordCount
    Text(
        text = sourceText(result),
        style = MaterialTheme.typography.bodyMedium,
        color = if (result.source ==
            BriefingSource.AI
        ) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    result.aiFailure?.detail?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        text = stringResource(
            R.string.preview_stats,
            pluralStringResource(R.plurals.preview_words, words, words),
            speakingSeconds(words),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = result.briefing.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun sourceText(result: BriefingResult): String {
    val usage = result.usage
    return if (result.source == BriefingSource.AI && usage != null) {
        stringResource(R.string.preview_source_ai, usage.model, usage.inputTokens, usage.outputTokens)
    } else {
        val reason = result.aiFailure?.reason ?: AiFailureReason.UNKNOWN
        stringResource(R.string.preview_source_template, stringResource(reason.messageRes()))
    }
}

/** Czech text-to-speech says about 150 words a minute at normal speed. */
private fun speakingSeconds(words: Int): Int = (words / WORDS_PER_MINUTE * 60).roundToInt()

private const val WORDS_PER_MINUTE = 150.0

@StringRes
private fun AiFailureReason.messageRes(): Int = when (this) {
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

@Preview(showBackground = true, locale = "cs", heightDp = 900)
@Composable
private fun PreviewScreenPreview() {
    val text = BriefingText(
        BriefingKind.MORNING,
        listOf(
            BriefingSection(
                SectionKind.GREETING_AND_WEATHER,
                "Dobré ráno, Oldo. Dnes je úterý sedmadvacátého října. V Plzni bude ráno tři stupně, odpoledne " +
                    "čtrnáct stupňů, skoro jasno. Srážky nehlásí.",
            ),
            BriefingSection(SectionKind.CALENDAR, "V kalendáři dnes nic nemáš."),
        ),
    )
    EliasTheme {
        PreviewScreen(
            uiState = PreviewUiState(
                isLoading = false,
                morning = BriefingResult(
                    text,
                    BriefingSource.TEMPLATE,
                    AiBriefingResult.Failure(AiFailureReason.NO_CREDIT, "HTTP 400 invalid_request_error"),
                    usage = null,
                ),
            ),
            onBack = {},
            onSelect = {},
            onUseAiChange = {},
            onRegenerate = {},
        )
    }
}
