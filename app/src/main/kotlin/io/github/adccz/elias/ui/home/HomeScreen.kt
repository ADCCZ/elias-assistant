package io.github.adccz.elias.ui.home

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.core.android.canPostNotifications
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.reading.ReadMark
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.ui.common.CzechFormat
import io.github.adccz.elias.ui.reading.ReadingMessage
import io.github.adccz.elias.ui.reading.ReadingMessageEffect
import io.github.adccz.elias.ui.reading.TodayReading
import io.github.adccz.elias.ui.reading.TodayReadingCard
import io.github.adccz.elias.ui.theme.EliasTheme
import io.github.adccz.elias.ui.voice.SpeechStatusText
import io.github.adccz.elias.ui.voice.SpeechUiState
import io.github.adccz.elias.ui.voice.openVoiceFix
import java.time.LocalDate

/**
 * Connects the screen to its ViewModel and to Android (voice settings, the notification permission).
 * Kept apart so [HomeScreen] is easy to preview.
 */
@Composable
fun HomeRoute(
    onOpenData: () -> Unit,
    onOpenPreview: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenReading: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(context.canPostNotifications()) }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    var notificationCardDismissed by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedForNotifications = true
        notificationsAllowed = context.canPostNotifications()
    }
    // Coming back from the settings: the user may have switched notifications on.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = context.canPostNotifications()
        onPauseOrDispose { }
    }
    val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForNotifications
    HomeScreen(
        uiState = uiState,
        notificationCard = when {
            notificationsAllowed || notificationCardDismissed -> NotificationCard.HIDDEN

            // Android asks once or twice. After a refusal, the switch is in the app's notification settings.
            canAsk -> NotificationCard.ASK

            else -> NotificationCard.SETTINGS
        },
        onPlay = viewModel::play,
        onStop = viewModel::stop,
        onFixVoice = { status -> context.openVoiceFix(status) },
        onAllowNotifications = {
            if (canAsk) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        },
        onDismissNotifications = { notificationCardDismissed = true },
        onOpenData = onOpenData,
        onOpenPreview = onOpenPreview,
        onOpenSchedule = onOpenSchedule,
        onOpenStatus = onOpenStatus,
        onOpenPlans = onOpenPlans,
        onOpenJournal = onOpenJournal,
        onOpenReading = onOpenReading,
        onMarkRead = viewModel::markRead,
        onUndoRead = viewModel::undoRead,
        onReadingMessageShown = viewModel::readingMessageShown,
    )
}

/** The card that asks for the notification permission, needed only for the Friday sabbath reminder. */
enum class NotificationCard { HIDDEN, ASK, SETTINGS }

/** Stateless home screen: it only draws [uiState] and reports clicks. */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    notificationCard: NotificationCard,
    onPlay: (BriefingKind) -> Unit,
    onStop: () -> Unit,
    onFixVoice: (VoiceStatus) -> Unit,
    onAllowNotifications: () -> Unit,
    onDismissNotifications: () -> Unit,
    onOpenData: () -> Unit,
    onOpenPreview: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenReading: () -> Unit,
    onMarkRead: () -> Unit,
    onUndoRead: (ReadMark) -> Unit,
    onReadingMessageShown: (ReadingMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = uiState.speech
    val snackbarHostState = remember { SnackbarHostState() }
    ReadingMessageEffect(uiState.readingMessage, snackbarHostState, onUndoRead, onReadingMessageShown)
    Scaffold(modifier = modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        // Centered while everything fits, scrollable when it does not (a small screen or large text).
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
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
                Spacer(modifier = Modifier.height(24.dp))
                TodayReadingCard(uiState.reading, onMarkRead)
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onOpenReading) {
                        Text(stringResource(R.string.home_open_reading))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onOpenPlans) {
                        Text(stringResource(R.string.home_open_plans))
                    }
                    FilledTonalButton(onClick = onOpenJournal) {
                        Text(stringResource(R.string.home_open_journal))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(onClick = onOpenData) {
                    Text(stringResource(R.string.home_open_data))
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(onClick = onOpenPreview) {
                    Text(stringResource(R.string.home_open_preview))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onOpenSchedule) {
                        Text(stringResource(R.string.home_open_schedule))
                    }
                    FilledTonalButton(onClick = onOpenStatus) {
                        Text(stringResource(R.string.home_open_status))
                    }
                }
                NotificationPermissionCard(notificationCard, onAllowNotifications, onDismissNotifications)
            }
        }
    }
}

/** Asks for notifications with the reason, never with a dialog on its own (quiet by design, D26). */
@Composable
private fun NotificationPermissionCard(card: NotificationCard, onAllow: () -> Unit, onDismiss: () -> Unit) {
    if (card == NotificationCard.HIDDEN) return
    Spacer(modifier = Modifier.height(32.dp))
    OutlinedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_notifications_text), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_notifications_later)) }
                TextButton(onClick = onAllow) {
                    val label = when (card) {
                        NotificationCard.SETTINGS -> R.string.home_notifications_settings
                        else -> R.string.home_notifications_allow
                    }
                    Text(stringResource(label))
                }
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
                reading = TodayReading(next = Chapter(BibleBooks.byAbbreviation("Mk")!!, 3)),
            ),
            notificationCard = NotificationCard.ASK,
            onPlay = {},
            onStop = {},
            onFixVoice = {},
            onAllowNotifications = {},
            onDismissNotifications = {},
            onOpenData = {},
            onOpenPreview = {},
            onOpenSchedule = {},
            onOpenStatus = {},
            onOpenPlans = {},
            onOpenJournal = {},
            onOpenReading = {},
            onMarkRead = {},
            onUndoRead = {},
            onReadingMessageShown = {},
        )
    }
}
