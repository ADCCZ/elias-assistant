package io.github.adccz.elias.ui.scheduled

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.ui.theme.EliasTheme
import io.github.adccz.elias.ui.voice.SpeechStatusText
import io.github.adccz.elias.ui.voice.SpeechUiState

/**
 * The screen of the scheduled evening briefing. A full-screen notification opens it over the lock screen and switches
 * the screen on (manifest: showWhenLocked, turnScreenOn), like an alarm clock. It starts the briefing, shows what
 * Elias is doing and closes itself when the briefing is over.
 */
@AndroidEntryPoint
class ScheduledBriefingActivity : ComponentActivity() {
    private val viewModel: ScheduledBriefingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The screen stays on while Elias speaks, then the phone may sleep again.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (savedInstanceState == null) viewModel.start()
        setContent {
            EliasTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(uiState.isOver) { if (uiState.isOver) finish() }
                ScheduledBriefingScreen(
                    speech = uiState.speech,
                    onStop = {
                        viewModel.stop()
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }
}

/** Big and calm: the name, what Elias is doing, Zastavit, and Zavřít to hide the screen while Elias goes on. */
@Composable
fun ScheduledBriefingScreen(
    speech: SpeechUiState,
    onStop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Text(stringResource(R.string.playback_title_evening), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))
            SpeechStatusText(speech, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            if (speech is SpeechUiState.Preparing || speech is SpeechUiState.Speaking) {
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.speech_stop)) }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.scheduled_close)) }
        }
    }
}

@Preview(showBackground = true, locale = "cs")
@Composable
private fun ScheduledBriefingScreenPreview() {
    EliasTheme {
        ScheduledBriefingScreen(speech = SpeechUiState.Speaking(BriefingKind.EVENING, isPaused = false), onStop = {
        }, onClose = {})
    }
}
