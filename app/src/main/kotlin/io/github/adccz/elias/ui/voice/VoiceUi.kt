package io.github.adccz.elias.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.VoiceStatus

/** One line about Elias speaking: preparing, speaking, paused, or why it does not speak. Nothing when idle. */
@Composable
fun SpeechStatusText(state: SpeechUiState, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val text = when (state) {
        SpeechUiState.Idle -> return

        is SpeechUiState.Preparing -> stringResource(
            when (state.kind) {
                BriefingKind.MORNING -> R.string.speech_preparing_morning
                BriefingKind.EVENING -> R.string.speech_preparing_evening
            },
        )

        is SpeechUiState.Speaking -> stringResource(
            when {
                state.isPaused -> R.string.speech_paused
                state.kind == BriefingKind.MORNING -> R.string.speech_speaking_morning
                else -> R.string.speech_speaking_evening
            },
        )

        is SpeechUiState.Failed -> problemText(state.problem)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state is SpeechUiState.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** The result of the Czech voice check, with a button that opens the right settings when something is missing. */
@Composable
fun VoiceStatusCard(status: VoiceStatus?, onFix: (VoiceStatus) -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (status == null) stringResource(R.string.voice_checking) else voiceStatusText(status),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (status != null && status !is VoiceStatus.Ready) {
                FilledTonalButton(onClick = { onFix(status) }) {
                    Text(stringResource(R.string.voice_fix))
                }
            }
        }
    }
}

/** What the voice check found, as one Czech sentence. */
@Composable
fun voiceStatusText(status: VoiceStatus): String = when (status) {
    is VoiceStatus.Ready -> stringResource(R.string.voice_ready, status.engine.label, status.voiceName)
    VoiceStatus.EngineUnavailable -> stringResource(R.string.voice_engine_unavailable)
    is VoiceStatus.NoCzech -> stringResource(R.string.voice_no_czech, status.engine.label)
    is VoiceStatus.NotDownloaded -> stringResource(R.string.voice_not_downloaded, status.engine.label)
    is VoiceStatus.OnlineOnly -> stringResource(R.string.voice_online_only, status.engine.label)
}

@Composable
private fun problemText(problem: SpeakResult.Problem): String = when (problem) {
    SpeakResult.NothingToSay -> stringResource(R.string.speech_failed_nothing)
    is SpeakResult.VoiceNotReady -> stringResource(R.string.speech_failed_voice, voiceStatusText(problem.status))
    is SpeakResult.SynthesisFailed -> stringResource(R.string.speech_failed_synthesis)
    SpeakResult.PlaybackFailed -> stringResource(R.string.speech_failed_playback)
}
