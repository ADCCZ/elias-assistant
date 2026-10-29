package io.github.adccz.elias.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.journal.JournalEntry
import io.github.adccz.elias.domain.journal.JournalText
import io.github.adccz.elias.ui.common.CzechFormat
import io.github.adccz.elias.ui.common.messageRes
import io.github.adccz.elias.ui.theme.EliasTheme
import java.time.Instant
import java.time.LocalDate

@Composable
fun JournalRoute(onBack: () -> Unit, viewModel: JournalViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Leaving the app or the screen: save what was typed right away.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.saveNow() }
    JournalScreen(
        uiState = uiState,
        onBack = {
            viewModel.saveNow()
            onBack()
        },
        onTextChange = viewModel::onTextChange,
        onOpen = viewModel::open,
        onSuggestStep = viewModel::suggestStep,
        onDismissStep = viewModel::dismissStep,
    )
}

/** Today's entry (or an older one) with its four fields, and the older entries below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    uiState: JournalUiState,
    onBack: () -> Unit,
    onTextChange: (LocalDate, JournalText) -> Unit,
    onOpen: (LocalDate) -> Unit,
    onSuggestStep: (LocalDate, String) -> Unit,
    onDismissStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_title)) },
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
            item {
                Text(dayTitle(uiState.editingDate, uiState.today), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.journal_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "editor-${uiState.editingDate}") {
                if (uiState.isLoaded) {
                    JournalEditor(
                        date = uiState.editingDate,
                        saved = uiState.entry,
                        step = uiState.step,
                        onTextChange = onTextChange,
                        onSuggestStep = onSuggestStep,
                        onDismissStep = onDismissStep,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (uiState.editingDate != uiState.today) {
                item {
                    TextButton(onClick = { onOpen(uiState.today) }) {
                        Text(stringResource(R.string.journal_back_to_today))
                    }
                }
            }
            item { Text(stringResource(R.string.journal_history), style = MaterialTheme.typography.titleMedium) }
            if (uiState.history.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.journal_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.history, key = { it.date.toEpochDay() }) { entry ->
                HistoryCard(entry, uiState.today, onClick = { onOpen(entry.date) })
            }
        }
    }
}

private val JournalTextSaver = listSaver<JournalText, String>(
    save = { listOf(it.thought, it.stuckWithMe, it.toImprove, it.step) },
    restore = { JournalText(it[0], it[1], it[2], it[3]) },
)

/** The four fields. The typed text lives here and every change goes up to be saved. */
@Composable
private fun JournalEditor(
    date: LocalDate,
    saved: JournalEntry?,
    step: StepUiState,
    onTextChange: (LocalDate, JournalText) -> Unit,
    onSuggestStep: (LocalDate, String) -> Unit,
    onDismissStep: () -> Unit,
) {
    var text by rememberSaveable(date, stateSaver = JournalTextSaver) { mutableStateOf(saved?.text ?: JournalText()) }
    fun change(new: JournalText) {
        text = new
        onTextChange(date, new)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalField(text.thought, R.string.journal_thought) { change(text.copy(thought = it)) }
        JournalField(text.stuckWithMe, R.string.journal_stuck) { change(text.copy(stuckWithMe = it)) }
        JournalField(text.toImprove, R.string.journal_improve) { change(text.copy(toImprove = it)) }
        JournalField(text.step, R.string.journal_step) { change(text.copy(step = it)) }
        StepTools(
            step = step,
            canSuggest = text.toImprove.isNotBlank(),
            onSuggest = { onSuggestStep(date, text.toImprove) },
            onUse = { suggested ->
                change(text.copy(step = suggested))
                onDismissStep()
            },
            onDismiss = onDismissStep,
        )
        saved?.let {
            Text(
                savedText(it.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Navrhni krok", what leaves the phone, and the suggestion with Použít and Zavřít. */
@Composable
private fun StepTools(
    step: StepUiState,
    canSuggest: Boolean,
    onSuggest: () -> Unit,
    onUse: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(onClick = onSuggest, enabled = canSuggest && step != StepUiState.Working) {
            Text(stringResource(R.string.journal_suggest_step))
        }
        Text(
            stringResource(R.string.journal_suggest_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (step) {
            StepUiState.Idle -> Unit

            StepUiState.Working -> Text(stringResource(R.string.journal_suggest_working))

            is StepUiState.Suggested -> OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.journal_suggestion, step.step))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.journal_dismiss)) }
                        TextButton(onClick = { onUse(step.step) }) { Text(stringResource(R.string.journal_use_step)) }
                    }
                }
            }

            is StepUiState.Failed -> Text(
                stringResource(R.string.journal_step_failed, stringResource(step.reason.messageRes())),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun JournalField(value: String, label: Int, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Composable
private fun HistoryCard(entry: JournalEntry, today: LocalDate, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dayTitle(entry.date, today), style = MaterialTheme.typography.titleSmall)
            val preview =
                with(entry.text) { listOf(thought, stuckWithMe, toImprove, step).firstOrNull { it.isNotBlank() } }
            preview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** "Dnes, čtvrtek 29. 10." or "Pondělí 26. 10.". */
@Composable
private fun dayTitle(date: LocalDate, today: LocalDate): String {
    val label = date.format(CzechFormat.dayAndDate)
    return if (date == today) {
        stringResource(R.string.journal_today, label)
    } else {
        label.replaceFirstChar { it.titlecase() }
    }
}

@Composable
private fun savedText(at: Instant): String {
    val local = at.atZone(PRAGUE_ZONE)
    return stringResource(R.string.journal_saved, local.format(CzechFormat.dayAndDate), local.format(CzechFormat.time))
}

@Preview(showBackground = true, locale = "cs")
@Composable
private fun JournalScreenPreview() {
    val today = LocalDate.of(2026, 10, 29)
    EliasTheme {
        JournalScreen(
            uiState = JournalUiState(
                today = today,
                editingDate = today,
                isLoaded = true,
                entry = JournalEntry(
                    today,
                    JournalText(thought = "Ticho pomáhá víc než rychlá odpověď.", toImprove = "Méně odkládat úkoly"),
                    Instant.parse("2026-10-29T21:15:00Z"),
                ),
                step = StepUiState.Suggested(today, "Zítra začni nejtěžším úkolem hned ráno a dokonči ho do oběda."),
                history = listOf(
                    JournalEntry(
                        today.minusDays(3),
                        JournalText(thought = "Ráno se vyplatí začít bez telefonu."),
                        Instant.EPOCH,
                    ),
                ),
            ),
            onBack = {},
            onTextChange = { _, _ -> },
            onOpen = {},
            onSuggestStep = { _, _ -> },
            onDismissStep = {},
        )
    }
}
