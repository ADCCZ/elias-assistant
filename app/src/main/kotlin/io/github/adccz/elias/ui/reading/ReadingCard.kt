package io.github.adccz.elias.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.reading.ReadMark

/**
 * Today's chapter with **Přečteno**, and what was read today. Nothing is drawn when the queue is empty and nothing was
 * read today.
 */
@Composable
fun TodayReadingCard(today: TodayReading, onMarkRead: () -> Unit, modifier: Modifier = Modifier) {
    val next = today.next
    if (next == null && today.readToday.isEmpty()) return
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (today.readToday.isNotEmpty()) {
                Text(
                    stringResource(R.string.reading_read_today, today.readToday.joinToString(", ") { it.label }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (next != null) {
                val title = if (today.readToday.isEmpty()) R.string.reading_next else R.string.reading_next_more
                Text(stringResource(title, next.label), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onMarkRead) { Text(stringResource(R.string.reading_mark_read)) }
            }
        }
    }
}

/** Shows [message] once in the snackbar. Vrátit takes the mark back. */
@Composable
fun ReadingMessageEffect(
    message: ReadingMessage?,
    snackbarHostState: SnackbarHostState,
    onUndo: (ReadMark) -> Unit,
    onShown: (ReadingMessage) -> Unit,
) {
    if (message == null) return
    val text = when (message) {
        is ReadingMessage.Marked -> stringResource(R.string.reading_marked, message.mark.chapter.label)
        ReadingMessage.NothingInPlan -> stringResource(R.string.reading_nothing_in_plan)
    }
    val undoLabel = stringResource(R.string.reading_undo)
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(message) {
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = undoLabel.takeIf { message is ReadingMessage.Marked },
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed && message is ReadingMessage.Marked) currentOnUndo(message.mark)
        currentOnShown(message)
    }
}
