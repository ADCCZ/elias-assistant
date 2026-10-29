package io.github.adccz.elias.ui.plans

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.topics.DayAgenda
import io.github.adccz.elias.domain.topics.Topic
import io.github.adccz.elias.domain.topics.TopicPlans
import io.github.adccz.elias.ui.common.CzechFormat
import io.github.adccz.elias.ui.theme.EliasTheme
import java.time.LocalDate

/** Connects the Plans screen to its ViewModel and to Android (calendar permission, the calendar app). */
@Composable
fun PlansRoute(onBack: () -> Unit, viewModel: PlansViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    // Coming back from the calendar app with a new plan: read the calendar again.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    PlansScreen(
        uiState = uiState,
        onBack = onBack,
        onGrantPermission = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
        onAddPlan = { date -> context.openNewPlan(date) },
        onSetTopic = viewModel::setTopic,
        onSetRead = viewModel::setRead,
    )
}

/** Opens the calendar app with a new all-day event on [date]. The user picks the topic calendar there. */
private fun Context.openNewPlan(date: LocalDate) {
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, date.atStartOfDay(PRAGUE_ZONE).toInstant().toEpochMilli())
        .putExtra(
            CalendarContract.EXTRA_EVENT_END_TIME,
            date.plusDays(1).atStartOfDay(PRAGUE_ZONE).toInstant().toEpochMilli(),
        )
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.plans_calendar_app_missing, Toast.LENGTH_SHORT).show()
    }
}

/** Stateless Plans screen: the next days by topic and the topic picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    uiState: PlansUiState,
    onBack: () -> Unit,
    onGrantPermission: () -> Unit,
    onAddPlan: (LocalDate) -> Unit,
    onSetTopic: (Long, Boolean) -> Unit,
    onSetRead: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plans_title)) },
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
            item { Text(stringResource(R.string.plans_intro), style = MaterialTheme.typography.bodyMedium) }
            if (uiState.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            when (uiState.problem) {
                null -> Unit
                FailureReason.NO_PERMISSION -> item { PermissionCard(onGrantPermission) }
                else -> item { Text(stringResource(R.string.failure_unknown)) }
            }
            if (!uiState.isLoading && uiState.problem == null) {
                // Always in the same place, so switching a topic on never moves the switches under the finger.
                // The setup help goes below it for the same reason: it disappears with the first topic.
                item(key = "topics") { TopicPicker(uiState.calendars, uiState.hasTopics, onSetTopic, onSetRead) }
                if (!uiState.hasTopics) {
                    item { Text(stringResource(R.string.plans_setup), style = MaterialTheme.typography.bodyMedium) }
                }
                items(uiState.days, key = { it.date.toEpochDay() }) { day ->
                    DayCard(day, uiState.today, uiState.topicColors, uiState.hasTopics, onAddPlan)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrantPermission: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.calendar_permission_explanation))
            Button(onClick = onGrantPermission) { Text(stringResource(R.string.calendar_grant)) }
        }
    }
}

@Composable
private fun DayCard(
    day: DayAgenda,
    today: LocalDate?,
    topicColors: Map<Long, Int>,
    hasTopics: Boolean,
    onAddPlan: (LocalDate) -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(dayTitle(day.date, today), style = MaterialTheme.typography.titleMedium)
            val withPlans = day.topicsWithPlans
            if (withPlans.isEmpty() && day.events.isEmpty()) {
                Text(stringResource(R.string.plans_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            withPlans.forEach { TopicBlock(it, topicColors[it.topic.calendarId]) }
            if (hasTopics && day.topicsWithoutPlans.isNotEmpty()) {
                Text(
                    stringResource(R.string.plans_without_plan, day.topicsWithoutPlans.joinToString(", ") { it.name }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (day.events.isNotEmpty()) {
                Text(
                    stringResource(R.string.plans_other_events),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                day.events.forEach { Text(itemText(it), style = MaterialTheme.typography.bodyMedium) }
            }
            TextButton(onClick = { onAddPlan(day.date) }) { Text(stringResource(R.string.plans_add)) }
        }
    }
}

@Composable
private fun TopicBlock(plans: TopicPlans, color: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorDot(color)
            Text(plans.topic.name, style = MaterialTheme.typography.titleSmall)
        }
        plans.plans.forEach { plan ->
            Text(
                itemText(plan),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
    }
}

/**
 * The topics. Open when the screen first shows no topics, otherwise one line with Upravit. It does not close by
 * itself when a topic is switched on, only with Hotovo.
 */
@Composable
private fun TopicPicker(
    calendars: List<CalendarChoice>,
    hasTopics: Boolean,
    onSetTopic: (Long, Boolean) -> Unit,
    onSetRead: (Long, Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(!hasTopics) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.plans_topics_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (hasTopics) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.plans_topics_done else R.string.plans_topics_edit))
                    }
                }
            }
            if (expanded || !hasTopics) {
                Text(stringResource(R.string.plans_topics_hint), style = MaterialTheme.typography.bodySmall)
                if (calendars.isEmpty()) Text(stringResource(R.string.plans_no_calendars))
                calendars.forEach { choice -> CalendarRow(choice, onSetTopic, onSetRead) }
            } else {
                val topics = calendars.filter { it.isTopic && it.isRead }.joinToString(", ") { it.calendar.name }
                Text(stringResource(R.string.plans_topics_summary, topics))
                val hidden = calendars.filterNot { it.isRead }
                if (hidden.isNotEmpty()) {
                    Text(
                        stringResource(R.string.plans_hidden_summary, hidden.joinToString(", ") { it.calendar.name }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(
    choice: CalendarChoice,
    onSetTopic: (Long, Boolean) -> Unit,
    onSetRead: (Long, Boolean) -> Unit,
) {
    val calendar = choice.calendar
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorDot(calendar.color)
        Column(modifier = Modifier.weight(1f)) {
            Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
            calendar.accountName?.takeIf { it != calendar.name }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!calendar.isShown) {
                Text(
                    stringResource(R.string.plans_calendar_not_shown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        LabeledSwitch(R.string.plans_switch_read, choice.isRead) { onSetRead(calendar.id, it) }
        // A calendar that is not read cannot be a topic.
        LabeledSwitch(R.string.plans_switch_topic, choice.isTopic && choice.isRead, enabled = choice.isRead) {
            onSetTopic(calendar.id, it)
        }
    }
}

@Composable
private fun LabeledSwitch(label: Int, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ColorDot(color: Int?) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/** "Dnes, čtvrtek 29. 10.", "Zítra, pátek 30. 10.", "Sobota 31. 10.". */
@Composable
private fun dayTitle(date: LocalDate, today: LocalDate?): String {
    val label = date.format(CzechFormat.dayAndDate)
    return when (date) {
        today -> stringResource(R.string.plans_today, label)
        today?.plusDays(1) -> stringResource(R.string.plans_tomorrow, label)
        else -> label.replaceFirstChar { it.titlecase() }
    }
}

/** "10:00 Připravit referát", or only the title for an all-day plan. */
@Composable
private fun itemText(event: CalendarEvent): String {
    val title = event.title.ifBlank { stringResource(R.string.calendar_untitled) }
    return if (event.isAllDay) {
        title
    } else {
        stringResource(
            R.string.plans_item_timed,
            event.start.format(CzechFormat.time),
            title,
        )
    }
}

@Preview(showBackground = true, locale = "cs")
@Composable
private fun PlansScreenPreview() {
    val today = LocalDate.of(2026, 10, 29)
    val school = Topic(2, "Škola")
    val spanish = Topic(4, "Španělština")
    fun event(title: String, hour: Int?, calendarId: Long) = CalendarEvent(
        title = title,
        start = today.atTime(hour ?: 0, 0).atZone(PRAGUE_ZONE),
        end = today.atTime((hour ?: 0) + 1, 0).atZone(PRAGUE_ZONE),
        isAllDay = hour == null,
        location = null,
        calendarName = null,
        calendarId = calendarId,
    )
    EliasTheme {
        PlansScreen(
            uiState = PlansUiState(
                isLoading = false,
                today = today,
                days = listOf(
                    DayAgenda(
                        today,
                        events = listOf(event("Zubař", 8, 1)),
                        topics = listOf(
                            TopicPlans(school, listOf(event("Připravit referát", null, 2))),
                            TopicPlans(spanish, emptyList()),
                        ),
                    ),
                    DayAgenda(today.plusDays(1), emptyList(), listOf(TopicPlans(school, emptyList()))),
                ),
                calendars = listOf(
                    CalendarChoice(
                        PhoneCalendar(1, "Narozeniny", "olda@example.com", 0xFF8E24AA.toInt(), true),
                        false,
                        false,
                    ),
                    CalendarChoice(PhoneCalendar(2, "Škola", "olda@example.com", 0xFF3366CC.toInt(), true), true),
                    CalendarChoice(PhoneCalendar(4, "Španělština", "olda@example.com", null, false), true),
                ),
                topicColors = mapOf(2L to 0xFF3366CC.toInt()),
            ),
            onBack = {},
            onGrantPermission = {},
            onAddPlan = {},
            onSetTopic = { _, _ -> },
            onSetRead = { _, _ -> },
        )
    }
}
