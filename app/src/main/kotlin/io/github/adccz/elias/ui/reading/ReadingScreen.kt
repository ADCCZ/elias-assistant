package io.github.adccz.elias.ui.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.bible.BibleBook
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.reading.ReadMark
import io.github.adccz.elias.domain.reading.ReadingQueue
import io.github.adccz.elias.domain.reading.ReadingSection
import io.github.adccz.elias.ui.theme.EliasTheme

@Composable
fun ReadingRoute(onBack: () -> Unit, viewModel: ReadingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReadingScreen(
        uiState = uiState,
        onBack = onBack,
        onMarkRead = viewModel::markRead,
        onUndo = viewModel::undo,
        onMessageShown = viewModel::messageShown,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onMove = viewModel::move,
        onContinueWith = viewModel::continueWith,
        onTakeOverFromPlan = viewModel::takeOverFromPlan,
    )
}

/** Today's chapter with Přečteno, then the queue: each section can move, be removed or be continued elsewhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    uiState: ReadingUiState,
    onBack: () -> Unit,
    onMarkRead: () -> Unit,
    onUndo: (ReadMark) -> Unit,
    onMessageShown: (ReadingMessage) -> Unit,
    onAdd: (BibleBook, Int, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onContinueWith: (Long, Int) -> Unit,
    onTakeOverFromPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ReadingMessageEffect(uiState.message, snackbarHostState, onUndo, onMessageShown)
    var adding by rememberSaveable { mutableStateOf(false) }
    var continuingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TodayReadingCard(uiState.today, onMarkRead) }
            item {
                Text(
                    stringResource(R.string.reading_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.reading_queue),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { adding = true }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                        Text(stringResource(R.string.reading_add), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (uiState.isLoaded && uiState.sections.isEmpty()) {
                item {
                    val text = if (uiState.finished) R.string.reading_finished else R.string.reading_empty
                    Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    OutlinedButton(onClick = onTakeOverFromPlan) { Text(stringResource(R.string.reading_take_over)) }
                }
            }
            items(uiState.sections, key = { it.id }) { section ->
                SectionRow(
                    section = section,
                    canMoveUp = section != uiState.sections.first(),
                    canMoveDown = section != uiState.sections.last(),
                    onClick = { continuingId = section.id },
                    onMove = { offset -> onMove(section.id, offset) },
                    onDelete = { deletingId = section.id },
                )
                HorizontalDivider()
            }
            if (uiState.sections.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.reading_continue_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (adding) {
        AddSectionDialog(
            onAdd = { book, from, to ->
                onAdd(book, from, to)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    uiState.sections.firstOrNull { it.id == continuingId }?.let { section ->
        ContinueDialog(
            section = section,
            onContinue = { chapter ->
                onContinueWith(section.id, chapter)
                continuingId = null
            },
            onDismiss = { continuingId = null },
        )
    }
    uiState.sections.firstOrNull { it.id == deletingId }?.let { section ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.reading_delete_confirm, section.label)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(section.id)
                    deletingId = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionRow(
    section: ReadingSection,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val left = section.last - section.next + 1
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(),
        headlineContent = { Text(section.label) },
        supportingContent = { Text(pluralStringResource(R.plurals.reading_chapters_left, left, left)) },
        trailingContent = {
            Row {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_upward),
                        contentDescription = stringResource(R.string.reading_move_up),
                    )
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_downward),
                        contentDescription = stringResource(R.string.reading_move_down),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.reading_delete),
                    )
                }
            }
        },
    )
}

/** Picks a book from the list and the chapters: the whole book unless the user changes them. */
@Composable
private fun AddSectionDialog(onAdd: (BibleBook, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var bookAbbreviation by rememberSaveable { mutableStateOf<String?>(null) }
    var fromText by rememberSaveable { mutableStateOf("") }
    var toText by rememberSaveable { mutableStateOf("") }
    val book = bookAbbreviation?.let(BibleBooks::byAbbreviation)
    val from = fromText.toIntOrNull()
    val to = toText.toIntOrNull()
    val rangeValid = book != null && from != null && to != null && from in 1..to && to <= book.chapters

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.reading_book), style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(BibleBooks.all, key = { it.abbreviation }) { candidate ->
                        val selected = candidate == book
                        ListItem(
                            modifier = Modifier.clickable {
                                bookAbbreviation = candidate.abbreviation
                                fromText = "1"
                                toText = candidate.chapters.toString()
                            },
                            headlineContent = { Text(candidate.name) },
                            colors = if (selected) {
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                ListItemDefaults.colors()
                            },
                        )
                    }
                }
                if (book != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChapterField(fromText, { fromText = it }, R.string.reading_from, Modifier.weight(1f))
                        ChapterField(toText, { toText = it }, R.string.reading_to, Modifier.weight(1f))
                    }
                    if (!rangeValid) {
                        Text(
                            stringResource(R.string.reading_range_error, book.chapters),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (rangeValid) onAdd(book!!, from!!, to!!) }, enabled = rangeValid) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Chooses the chapter of [section] to continue with. */
@Composable
private fun ContinueDialog(section: ReadingSection, onContinue: (Int) -> Unit, onDismiss: () -> Unit) {
    var chapterText by rememberSaveable(section.id) { mutableStateOf(section.next.toString()) }
    val chapter = chapterText.toIntOrNull()?.takeIf { it in 1..section.last }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_continue_title, section.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChapterField(chapterText, { chapterText = it }, R.string.reading_continue_chapter)
                if (chapter == null) {
                    Text(
                        stringResource(R.string.reading_continue_error, section.last),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { chapter?.let(onContinue) }, enabled = chapter != null) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChapterField(value: String, onValueChange: (String) -> Unit, label: Int, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(MAX_DIGITS)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private const val MAX_DIGITS = 3

@Preview(showBackground = true, locale = "cs")
@Composable
private fun ReadingScreenPreview() {
    val mark = BibleBooks.byAbbreviation("Mk")!!
    val queue = ReadingQueue().add(
        mark,
    ).add(BibleBooks.byAbbreviation("Sk")!!).add(BibleBooks.byAbbreviation("J")!!, 13, 13)
        .advance()
        .advance()
    EliasTheme {
        ReadingScreen(
            uiState = ReadingUiState(
                isLoaded = true,
                sections = queue.sections,
                today = TodayReading(queue.next, listOf(Chapter(mark, 2))),
            ),
            onBack = {},
            onMarkRead = {},
            onUndo = {},
            onMessageShown = {},
            onAdd = { _, _, _ -> },
            onRemove = {},
            onMove = { _, _ -> },
            onContinueWith = { _, _ -> },
            onTakeOverFromPlan = {},
        )
    }
}
