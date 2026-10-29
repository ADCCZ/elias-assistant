package io.github.adccz.elias.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.bible.BibleBook
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.reading.BibleReading
import io.github.adccz.elias.domain.reading.ReadMark
import io.github.adccz.elias.domain.reading.ReadingQueue
import io.github.adccz.elias.domain.reading.ReadingSection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A short message under the screen. Shown once, then the screen reports it as shown. */
sealed interface ReadingMessage {
    /** "Přečteno: Marek 3" with Vrátit. */
    data class Marked(val mark: ReadMark) : ReadingMessage

    /** "Převzít z plánu" found no readings from today on. */
    data object NothingInPlan : ReadingMessage
}

/** Today's chapter and what was read today: shown on the reading screen and on the home screen. */
data class TodayReading(val next: Chapter? = null, val readToday: List<Chapter> = emptyList())

/** The reading screen: the queue, today's reading and a message. */
data class ReadingUiState(
    val isLoaded: Boolean = false,
    val sections: List<ReadingSection> = emptyList(),
    val finished: Boolean = false,
    val today: TodayReading = TodayReading(),
    val message: ReadingMessage? = null,
)

/**
 * The Bible reading queue (spec, M5): Přečteno with Vrátit, adding, moving and removing sections, choosing the chapter
 * to continue with and taking the readings over from the plan. The rules live in [BibleReading] and [ReadingQueue].
 */
@HiltViewModel
class ReadingViewModel @Inject constructor(private val reading: BibleReading) : ViewModel() {
    private val marks = ReadMarks(reading, viewModelScope)

    val uiState: StateFlow<ReadingUiState> = combine(
        reading.queue,
        reading.readOn(reading.today()),
        marks.message,
    ) { queue, read, message ->
        ReadingUiState(
            isLoaded = true,
            sections = queue.sections,
            finished = queue.finished,
            today = TodayReading(queue.next, read.map { it.chapter }),
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReadingUiState())

    fun markRead() = marks.markRead()

    fun undo(mark: ReadMark) = marks.undo(mark)

    fun messageShown(message: ReadingMessage) = marks.shown(message)

    fun add(book: BibleBook, from: Int, to: Int) {
        if (from !in 1..to || to > book.chapters) return
        edit { it.add(book, from, to) }
    }

    fun remove(id: Long) = edit { it.remove(id) }

    fun move(id: Long, offset: Int) = edit { it.move(id, offset) }

    /** Reading goes on with [chapter] of the section [id]. A chapter outside the section is ignored. */
    fun continueWith(id: Long, chapter: Int) = edit { queue ->
        val section = queue.sections.firstOrNull { it.id == id }
        if (section == null || chapter !in 1..section.last) queue else queue.continueWith(id, chapter)
    }

    fun takeOverFromPlan() {
        viewModelScope.launch {
            if (!reading.takeOverFromPlan()) marks.show(ReadingMessage.NothingInPlan)
        }
    }

    private fun edit(change: (ReadingQueue) -> ReadingQueue) {
        viewModelScope.launch { reading.edit(change) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Přečteno and Vrátit with their message, shared by the reading screen and the home screen. The message stays until
 * the screen has shown it, so it survives a rotation.
 */
internal class ReadMarks(private val reading: BibleReading, private val scope: CoroutineScope) {
    private val current = MutableStateFlow<ReadingMessage?>(null)

    val message: StateFlow<ReadingMessage?> get() = current

    fun markRead() {
        scope.launch {
            reading.markRead()?.let { show(ReadingMessage.Marked(it)) }
        }
    }

    fun undo(mark: ReadMark) {
        scope.launch { reading.undo(mark) }
    }

    fun show(message: ReadingMessage) {
        current.value = message
    }

    /** Clears [message], but not a newer one that came meanwhile. */
    fun shown(message: ReadingMessage) = current.update { if (it == message) null else it }
}

/** Today's reading as a flow, for the home screen. */
internal fun BibleReading.todayReading() = combine(queue, readOn(today())) { queue, read ->
    TodayReading(queue.next, read.map { it.chapter })
}
