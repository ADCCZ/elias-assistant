package io.github.adccz.elias.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.schedule.DayTimes
import io.github.adccz.elias.domain.schedule.ScheduledRun
import io.github.adccz.elias.domain.schedule.ScheduledTask
import io.github.adccz.elias.domain.schedule.WeeklySchedule
import io.github.adccz.elias.ui.common.CzechFormat
import io.github.adccz.elias.ui.theme.EliasTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleRoute(onBack: () -> Unit, viewModel: ScheduleViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleScreen(uiState = uiState, onBack = onBack, onSetTime = viewModel::setTime)
}

/** The morning and evening time of each day, what is planned next, and the sabbath rules on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onBack: () -> Unit,
    onSetTime: (DayOfWeek, TimeSlot, LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The time being edited: day and slot, saved as text so it survives a screen rotation.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title)) },
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
            item { Text(stringResource(R.string.schedule_intro), style = MaterialTheme.typography.bodyMedium) }
            item { NextRuns(uiState.nextRuns) }
            items(DayOfWeek.entries) { day ->
                DayRow(day, uiState.schedule.on(day), onEdit = { slot -> editing = "${day.name}:${slot.name}" })
            }
            item {
                Text(
                    stringResource(R.string.schedule_sabbath_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    editing?.split(":")?.let { (dayName, slotName) ->
        val day = DayOfWeek.valueOf(dayName)
        val slot = TimeSlot.valueOf(slotName)
        TimeDialog(
            title = stringResource(
                if (slot == TimeSlot.MORNING) R.string.schedule_dialog_morning else R.string.schedule_dialog_evening,
                day.czechName(),
            ),
            initial = uiState.schedule.on(day).timeOf(slot) ?: defaultOf(slot),
            onDismiss = { editing = null },
            onSave = { time ->
                onSetTime(day, slot, time)
                editing = null
            },
        )
    }
}

@Composable
private fun NextRuns(runs: List<ScheduledRun>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.schedule_next), style = MaterialTheme.typography.titleSmall)
            if (runs.isEmpty()) Text(stringResource(R.string.schedule_nothing))
            runs.forEach { run -> Text(scheduledRunText(run), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** "Večerní souhrn: čtvrtek 29. 10. ve 22:00", shared with the status screen. */
@Composable
fun scheduledRunText(run: ScheduledRun): String = stringResource(
    R.string.schedule_run_at,
    stringResource(
        when (run.task) {
            ScheduledTask.PREFETCH -> R.string.schedule_task_prefetch
            ScheduledTask.EVENING_BRIEFING -> R.string.schedule_task_evening
            ScheduledTask.SABBATH_REMINDER -> R.string.schedule_task_reminder
        },
    ),
    run.at.format(CzechFormat.dayAndDate),
    run.at.format(CzechFormat.time),
)

@Composable
private fun DayRow(day: DayOfWeek, times: DayTimes, onEdit: (TimeSlot) -> Unit) {
    Column {
        Text(day.czechName(), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onEdit(TimeSlot.MORNING) },
                label = { Text(stringResource(R.string.schedule_morning, times.morning.labelOrOff())) },
            )
            if (day == DayOfWeek.FRIDAY) {
                // The sabbath begins at sunset on Friday: no evening briefing to set.
                AssistChip(onClick = {
                }, enabled = false, label = { Text(stringResource(R.string.schedule_friday_evening)) })
            } else {
                AssistChip(
                    onClick = { onEdit(TimeSlot.EVENING) },
                    label = { Text(stringResource(R.string.schedule_evening, times.evening.labelOrOff())) },
                )
            }
        }
    }
}

/** A clock face to pick the time, with Vypnout to switch this time off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onSave: (LocalTime?) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onSave(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.schedule_switch_off)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun LocalTime?.labelOrOff(): String = this?.format(CzechFormat.time) ?: stringResource(R.string.schedule_off)

private fun DayTimes.timeOf(slot: TimeSlot): LocalTime? = when (slot) {
    TimeSlot.MORNING -> morning
    TimeSlot.EVENING -> evening
}

private fun defaultOf(slot: TimeSlot): LocalTime = when (slot) {
    TimeSlot.MORNING -> WeeklySchedule.DEFAULT_MORNING
    TimeSlot.EVENING -> WeeklySchedule.DEFAULT_EVENING
}

/** "Pondělí". */
private fun DayOfWeek.czechName(): String =
    getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("cs-CZ")).replaceFirstChar { it.titlecase() }

@Preview(showBackground = true, locale = "cs")
@Composable
private fun ScheduleScreenPreview() {
    EliasTheme {
        ScheduleScreen(
            uiState = ScheduleUiState(
                schedule = WeeklySchedule.DEFAULT.with(DayOfWeek.SUNDAY, DayTimes(null, LocalTime.of(21, 30))),
            ),
            onBack = {},
            onSetTime = { _, _, _ -> },
        )
    }
}
