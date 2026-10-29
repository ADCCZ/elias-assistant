package io.github.adccz.elias.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.core.coroutines.ApplicationScope
import io.github.adccz.elias.domain.journal.Journal
import io.github.adccz.elias.domain.journal.JournalEntry
import io.github.adccz.elias.domain.journal.JournalText
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The journal screen. [entry] is the saved entry of [editingDate], [isLoaded] tells whether it was read already
 * (the editor starts only then, so it never shows an empty form over an existing entry). [history] has the other days.
 */
data class JournalUiState(
    val today: LocalDate,
    val editingDate: LocalDate,
    val isLoaded: Boolean = false,
    val entry: JournalEntry? = null,
    val history: List<JournalEntry> = emptyList(),
)

/**
 * Lets the user write today's entry and open older ones. The text being typed stays in the screen (Compose state is
 * the safe home for text field state). The screen reports every change, and the entry is saved shortly after the user
 * stops typing, when the screen pauses and when another day is opened. Saving runs in the app's scope, so it finishes
 * even when the screen is closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journal: Journal,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val today = journal.today()
    private val editingDate = MutableStateFlow(today)

    val uiState: StateFlow<JournalUiState> = combine(
        editingDate.flatMapLatest { date -> journal.entryOn(date).map { date to it } },
        journal.entries,
        editingDate,
    ) { (loadedDate, entry), entries, editing ->
        JournalUiState(
            today = today,
            editingDate = editing,
            isLoaded = loadedDate == editing,
            entry = entry.takeIf { loadedDate == editing },
            history = entries.filter { it.date != editing },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), JournalUiState(today, today))

    private var pending: Pair<LocalDate, JournalText>? = null
    private var debounce: Job? = null

    /** The user changed the text of [date]'s entry. It is saved once typing pauses. */
    fun onTextChange(date: LocalDate, text: JournalText) {
        pending = date to text
        debounce?.cancel()
        debounce = viewModelScope.launch {
            delay(SAVE_DELAY_MILLIS)
            saveNow()
        }
    }

    /** Saves what was typed and not saved yet. Called when the screen pauses. */
    fun saveNow() {
        val (date, text) = pending ?: return
        pending = null
        debounce?.cancel()
        appScope.launch { journal.save(date, text) }
    }

    /** Opens the entry of [date] (an older day from the history, or today again). */
    fun open(date: LocalDate) {
        saveNow()
        editingDate.value = date
    }

    override fun onCleared() {
        saveNow()
    }

    companion object {
        /** Long enough not to write on every letter, short enough that nothing typed gets lost. */
        const val SAVE_DELAY_MILLIS = 800L

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
