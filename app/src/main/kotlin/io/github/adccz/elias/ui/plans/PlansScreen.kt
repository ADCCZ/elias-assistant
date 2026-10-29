package io.github.adccz.elias.ui.plans

import android.Manifest
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.calendar.CalendarAccount
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.topics.DayAgenda
import io.github.adccz.elias.domain.topics.Topic
import io.github.adccz.elias.domain.topics.TopicPlans
import io.github.adccz.elias.ui.common.CzechFormat
import io.github.adccz.elias.ui.theme.EliasTheme
import java.time.LocalDate

/** Connects the Plans screen to its ViewModel and to Android (the calendar permission). */
@Composable
fun PlansRoute(onBack: () -> Unit, viewModel: PlansViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        onSetTopic = viewModel::setTopic,
        onSetRead = viewModel::setRead,
        onSetAccountRead = viewModel::setAccountRead,
    )
}

/** Stateless Plans screen: the calendar picker and the next days by topic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    uiState: PlansUiState,
    onBack: () -> Unit,
    onGrantPermission: () -> Unit,
    onSetTopic: (Long, Boolean) -> Unit,
    onSetRead: (Long, Boolean) -> Unit,
    onSetAccountRead: (CalendarAccount, Boolean) -> Unit,
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
                item(key = "calendars") {
                    CalendarPicker(uiState.accounts, uiState.hasTopics, onSetTopic, onSetRead, onSetAccountRead)
                }
                if (!uiState.hasTopics) {
                    item { Text(stringResource(R.string.plans_setup), style = MaterialTheme.typography.bodyMedium) }
                }
                items(uiState.days, key = { it.date.toEpochDay() }) { day ->
                    DayCard(day, uiState.today, uiState.calendarColors, uiState.hasTopics)
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

/**
 * One day: the plans of each topic, then a line and the other events. Every item has its calendar's color and its
 * time in a column of its own, so the items stay apart at a glance.
 */
@Composable
private fun DayCard(day: DayAgenda, today: LocalDate?, colors: Map<Long, Int>, hasTopics: Boolean) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dayTitle(day.date, today), style = MaterialTheme.typography.titleMedium)
            val withPlans = day.topicsWithPlans
            if (withPlans.isEmpty() && day.events.isEmpty()) {
                Text(stringResource(R.string.plans_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            withPlans.forEach { TopicBlock(it, colors) }
            if (hasTopics && day.topicsWithoutPlans.isNotEmpty()) {
                Text(
                    stringResource(R.string.plans_without_plan, day.topicsWithoutPlans.joinToString(", ") { it.name }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (day.events.isNotEmpty()) {
                if (withPlans.isNotEmpty() || hasTopics) HorizontalDivider()
                Text(
                    stringResource(R.string.plans_other_events),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                day.events.forEach { EventRow(it, colors[it.calendarId]) }
            }
        }
    }
}

@Composable
private fun TopicBlock(plans: TopicPlans, colors: Map<Long, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(plans.topic.name, style = MaterialTheme.typography.titleSmall)
        plans.plans.forEach { EventRow(it, colors[plans.topic.calendarId]) }
    }
}

/** "● 8:30  Lekce" or "● celý den  Výlet". */
@Composable
private fun EventRow(event: CalendarEvent, color: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorDot(color)
        Text(
            if (event.isAllDay) stringResource(R.string.calendar_all_day) else event.start.format(CzechFormat.time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(TIME_COLUMN),
        )
        Text(
            event.title.ifBlank { stringResource(R.string.calendar_untitled) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private val TIME_COLUMN = 60.dp

/**
 * The phone's calendars by account. Open when the screen first shows no topics, otherwise a short summary with
 * Upravit. It does not close by itself when a switch changes, only with Hotovo.
 */
@Composable
private fun CalendarPicker(
    accounts: List<AccountChoices>,
    hasTopics: Boolean,
    onSetTopic: (Long, Boolean) -> Unit,
    onSetRead: (Long, Boolean) -> Unit,
    onSetAccountRead: (CalendarAccount, Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(!hasTopics) }
    val calendars = accounts.flatMap { it.calendars }
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
                if (accounts.isEmpty()) Text(stringResource(R.string.plans_no_calendars))
                accounts.forEach { group ->
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    AccountHeader(group, onSetAccountRead)
                    group.calendars.forEach { choice -> CalendarRow(choice, onSetTopic, onSetRead) }
                }
            } else {
                val topics = calendars.filter { it.isTopic && it.isRead }.joinToString(", ") { it.calendar.name }
                Text(stringResource(R.string.plans_topics_summary, topics))
                val hidden = calendars.count { !it.isRead }
                if (hidden > 0) {
                    Text(
                        pluralStringResource(R.plurals.plans_hidden_count, hidden, hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The account's name with "Číst vše", which switches all its calendars at once. */
@Composable
private fun AccountHeader(group: AccountChoices, onSetAccountRead: (CalendarAccount, Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            accountLabel(group.account),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        LabeledSwitch(R.string.plans_switch_read_all, group.allRead) { onSetAccountRead(group.account, it) }
    }
}

@Composable
private fun accountLabel(account: CalendarAccount): String = when (account) {
    is CalendarAccount.Online -> account.name
    CalendarAccount.Samsung -> stringResource(R.string.plans_account_samsung)
    CalendarAccount.Phone -> stringResource(R.string.plans_account_phone)
}

@Composable
private fun CalendarRow(
    choice: CalendarChoice,
    onSetTopic: (Long, Boolean) -> Unit,
    onSetRead: (Long, Boolean) -> Unit,
) {
    val calendar = choice.calendar
    // A calendar whose events never reach the phone is shown faded: it changes nothing until it is synced.
    Row(
        modifier = Modifier.alpha(if (calendar.isShown) 1f else FADED),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ColorDot(calendar.color)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (calendar.isMain) stringResource(R.string.plans_main_calendar) else calendar.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!calendar.isShown) {
                Text(
                    stringResource(R.string.plans_calendar_not_shown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private const val FADED = 0.55f

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

@Preview(showBackground = true, locale = "cs", heightDp = 1400)
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
    val account = CalendarAccount.Online("olda@example.com")
    EliasTheme {
        PlansScreen(
            uiState = PlansUiState(
                isLoading = false,
                today = today,
                days = listOf(
                    DayAgenda(
                        today,
                        events = listOf(event("Zubař", 8, 1), event("Výlet", null, 1)),
                        topics = listOf(
                            TopicPlans(school, listOf(event("Připravit referát", null, 2), event("Seminář", 14, 2))),
                            TopicPlans(spanish, emptyList()),
                        ),
                    ),
                    DayAgenda(today.plusDays(1), emptyList(), listOf(TopicPlans(school, emptyList()))),
                ),
                accounts = listOf(
                    AccountChoices(
                        account,
                        listOf(
                            CalendarChoice(PhoneCalendar(5, "olda@example.com", "olda@example.com", null, true), false),
                            CalendarChoice(
                                PhoneCalendar(2, "Škola", "olda@example.com", 0xFF3366CC.toInt(), true),
                                true,
                            ),
                            CalendarChoice(PhoneCalendar(4, "Španělština", "olda@example.com", null, false), true),
                        ),
                    ),
                    AccountChoices(
                        CalendarAccount.Phone,
                        listOf(
                            CalendarChoice(
                                PhoneCalendar(1, "My calendar", null, 0xFF8E24AA.toInt(), true),
                                isTopic = false,
                                isRead = false,
                            ),
                        ),
                    ),
                ),
                calendarColors = mapOf(1L to 0xFF8E24AA.toInt(), 2L to 0xFF3366CC.toInt()),
            ),
            onBack = {},
            onGrantPermission = {},
            onSetTopic = { _, _ -> },
            onSetRead = { _, _ -> },
            onSetAccountRead = { _, _ -> },
        )
    }
}
