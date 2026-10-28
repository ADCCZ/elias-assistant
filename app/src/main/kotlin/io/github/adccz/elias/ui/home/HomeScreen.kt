package io.github.adccz.elias.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.ui.common.CzechFormat
import io.github.adccz.elias.ui.theme.EliasTheme
import io.github.adccz.elias.ui.voice.SpeechStatusText
import io.github.adccz.elias.ui.voice.SpeechUiState
import io.github.adccz.elias.ui.voice.openVoiceFix
import java.time.LocalDate

/** Connects the screen to its ViewModel and to Android (voice settings). Kept apart so [HomeScreen] is easy to preview. */
@Composable
fun HomeRoute(onOpenData: () -> Unit, onOpenPreview: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HomeScreen(
        uiState = uiState,
        onPlay = viewModel::play,
        onStop = viewModel::stop,
        onFixVoice = { status -> context.openVoiceFix(status) },
        onOpenData = onOpenData,
        onOpenPreview = onOpenPreview,
    )
}

/** Stateless home screen: it only draws [uiState] and reports clicks. */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onPlay: (BriefingKind) -> Unit,
    onStop: () -> Unit,
    onFixVoice: (VoiceStatus) -> Unit,
    onOpenData: () -> Unit,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = uiState.speech
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.home_today, uiState.today.format(CzechFormat.fullDate)),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            val isPreparing = speech is SpeechUiState.Preparing
            Button(onClick = { onPlay(BriefingKind.MORNING) }, enabled = !isPreparing) {
                Text(stringResource(R.string.home_play_morning))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { onPlay(BriefingKind.EVENING) }, enabled = !isPreparing) {
                Text(stringResource(R.string.home_play_evening))
            }
            SpeechControls(speech = speech, onStop = onStop, onFixVoice = onFixVoice)
            Spacer(modifier = Modifier.height(32.dp))
            FilledTonalButton(onClick = onOpenData) {
                Text(stringResource(R.string.home_open_data))
            }
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpenPreview) {
                Text(stringResource(R.string.home_open_preview))
            }
        }
    }
}

/** What Elias is doing, with Zastavit while it prepares or speaks and Opravit when the voice is missing. */
@Composable
private fun SpeechControls(speech: SpeechUiState, onStop: () -> Unit, onFixVoice: (VoiceStatus) -> Unit) {
    if (speech == SpeechUiState.Idle) return
    Spacer(modifier = Modifier.height(16.dp))
    SpeechStatusText(speech, textAlign = TextAlign.Center)
    if (speech is SpeechUiState.Preparing || speech is SpeechUiState.Speaking) {
        OutlinedButton(onClick = onStop) {
            Text(stringResource(R.string.speech_stop))
        }
    }
    val problem = (speech as? SpeechUiState.Failed)?.problem
    if (problem is SpeakResult.VoiceNotReady) {
        TextButton(onClick = { onFixVoice(problem.status) }) {
            Text(stringResource(R.string.voice_fix))
        }
    }
}

@Preview(showBackground = true, locale = "cs")
@Composable
private fun HomeScreenPreview() {
    EliasTheme {
        HomeScreen(
            uiState = HomeUiState(
                today = LocalDate.of(2026, 10, 27),
                speech = SpeechUiState.Speaking(BriefingKind.MORNING, isPaused = false),
            ),
            onPlay = {},
            onStop = {},
            onFixVoice = {},
            onOpenData = {},
            onOpenPreview = {},
        )
    }
}
