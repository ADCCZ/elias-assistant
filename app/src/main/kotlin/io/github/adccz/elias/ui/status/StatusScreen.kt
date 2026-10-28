package io.github.adccz.elias.ui.status

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.log.SourceOutcome
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.ui.common.CzechFormat
import io.github.adccz.elias.ui.schedule.scheduledRunText
import io.github.adccz.elias.ui.voice.VoiceStatusCard
import io.github.adccz.elias.ui.voice.openVoiceFix

/** Connects the status screen to its ViewModel and to Android (permission dialogs, system settings). */
@Composable
fun StatusRoute(onBack: () -> Unit, viewModel: StatusViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Android shows a permission dialog only once or twice. After that the switch is in the settings.
    var askedForCalendar by rememberSaveable { mutableStateOf(false) }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedForCalendar = true
        viewModel.refresh()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedForNotifications = true
        viewModel.refresh()
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    StatusScreen(
        uiState = uiState,
        onBack = onBack,
        onFix = { fix ->
            when (fix) {
                Fix.CALENDAR -> if (askedForCalendar) {
                    context.openAppSettings()
                } else {
                    calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                }

                Fix.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !askedForNotifications
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }

                Fix.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }

                Fix.BATTERY -> context.requestBatteryExemption()

                Fix.SAMSUNG_GUIDE -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SAMSUNG_GUIDE_URL)))
            }
        },
        onFixVoice = { status -> context.openVoiceFix(status) },
    )
}

/** What the user can fix from the status screen. */
enum class Fix { CALENDAR, NOTIFICATIONS, EXACT_ALARMS, BATTERY, SAMSUNG_GUIDE }

/** How Samsung phones stop apps in the background, and what to switch off (spec: dontkillmyapp.com for the phone). */
private const val SAMSUNG_GUIDE_URL = "https://dontkillmyapp.com/samsung"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    uiState: StatusUiState,
    onBack: () -> Unit,
    onFix: (Fix) -> Unit,
    onFixVoice: (VoiceStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.status_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.status_intro), style = MaterialTheme.typography.bodyMedium) }
            uiState.system?.let { system ->
                item {
                    CheckRow(
                        R.string.status_calendar,
                        system.calendar,
                        R.string.status_calendar_ok,
                        R.string.status_calendar_bad,
                    ) {
                        onFix(Fix.CALENDAR)
                    }
                }
                item {
                    CheckRow(
                        R.string.status_notifications,
                        system.notifications,
                        R.string.status_notifications_ok,
                        R.string.status_notifications_bad,
                    ) { onFix(Fix.NOTIFICATIONS) }
                }
                item {
                    CheckRow(
                        R.string.status_exact_alarms,
                        system.exactAlarms,
                        R.string.status_exact_alarms_ok,
                        R.string.status_exact_alarms_bad,
                    ) { onFix(Fix.EXACT_ALARMS) }
                }
                item {
                    CheckRow(
                        R.string.status_battery,
                        system.batteryUnrestricted,
                        R.string.status_battery_ok,
                        R.string.status_battery_bad,
                    ) {
                        onFix(Fix.BATTERY)
                    }
                }
            }
            item { VoiceStatusCard(status = uiState.voice, onFix = onFixVoice) }
            item {
                InfoCard(R.string.schedule_next) {
                    if (uiState.nextRuns.isEmpty()) Text(stringResource(R.string.schedule_nothing))
                    uiState.nextRuns.forEach { Text(scheduledRunText(it), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                InfoCard(R.string.status_last_run) {
                    val run = uiState.lastRun
                    if (run == null) Text(stringResource(R.string.status_no_run)) else LastRun(run)
                }
            }
            item {
                TextButton(onClick = {
                    onFix(Fix.SAMSUNG_GUIDE)
                }) { Text(stringResource(R.string.status_samsung_guide)) }
            }
        }
    }
}

/** One thing Elias needs: ✓ with what it means, or ✗ with what is missing and Opravit. */
@Composable
private fun CheckRow(title: Int, ok: Boolean, okText: Int, problemText: Int, onFix: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(
                        if (ok) R.string.status_ok else R.string.status_problem,
                        stringResource(if (ok) okText else problemText),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            if (!ok) TextButton(onClick = onFix) { Text(stringResource(R.string.voice_fix)) }
        }
    }
}

@Composable
private fun InfoCard(title: Int, content: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** "Večerní souhrn (naplánovaný), čtvrtek 29. 10. ve 22:00: zazněl. Napsala šablona. Zdroje: 5 načteno…" */
@Composable
private fun LastRun(run: RunRecord) {
    val startedAt = run.startedAt.atZone(PRAGUE_ZONE)
    val kind = stringResource(
        when (run.kind) {
            RunKind.MORNING_BRIEFING -> R.string.status_run_morning
            RunKind.EVENING_BRIEFING -> R.string.status_run_evening
            RunKind.PREFETCH -> R.string.schedule_task_prefetch
        },
    )
    val trigger = stringResource(
        if (run.trigger ==
            RunTrigger.SCHEDULE
        ) {
            R.string.status_run_scheduled
        } else {
            R.string.status_run_manual
        },
    )
    val outcome = stringResource(
        when (run.outcome) {
            RunOutcome.PLAYED -> R.string.status_outcome_played
            RunOutcome.PREFETCHED -> R.string.status_outcome_prefetched
            RunOutcome.NOTHING_TO_SAY -> R.string.status_outcome_nothing
            RunOutcome.VOICE_NOT_READY -> R.string.status_outcome_voice
            RunOutcome.SYNTHESIS_FAILED -> R.string.status_outcome_synthesis
            RunOutcome.PLAYBACK_FAILED -> R.string.status_outcome_playback
        },
    )
    Text(
        stringResource(
            R.string.status_run_summary,
            kind,
            trigger,
            startedAt.format(CzechFormat.dayAndDate),
            startedAt.format(CzechFormat.time),
            outcome,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    run.writtenBy?.let { source ->
        Text(
            stringResource(
                if (source ==
                    BriefingSource.AI
                ) {
                    R.string.status_written_by_ai
                } else {
                    R.string.status_written_by_template
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (run.sources.isNotEmpty()) {
        Text(
            stringResource(
                R.string.status_sources,
                run.sources.count { it.outcome == SourceOutcome.LOADED },
                run.sources.count { it.outcome == SourceOutcome.FROM_CACHE },
                run.sources.count { it.outcome == SourceOutcome.FAILED },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Context.openAppSettings() =
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

/**
 * Asks Android to leave Elias out of battery optimization, so the prefetch is not put to sleep at night.
 * Google Play allows this direct request only for some kinds of apps. Elias is not on Google Play.
 */
@SuppressLint("BatteryLife")
private fun Context.requestBatteryExemption() = startActivity(
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
)
