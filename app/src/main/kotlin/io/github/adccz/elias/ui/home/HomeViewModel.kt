package io.github.adccz.elias.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.reading.BibleReading
import io.github.adccz.elias.domain.reading.ReadMark
import io.github.adccz.elias.ui.reading.ReadMarks
import io.github.adccz.elias.ui.reading.ReadingMessage
import io.github.adccz.elias.ui.reading.TodayReading
import io.github.adccz.elias.ui.reading.todayReading
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything the home screen shows. The screen only renders this, it holds no logic. */
data class HomeUiState(
    val today: LocalDate,
    val speech: SpeechUiState = SpeechUiState.Idle,
    /** Today's chapter of the reading queue (M5), with Přečteno right on the home screen. */
    val reading: TodayReading = TodayReading(),
    val readingMessage: ReadingMessage? = null,
)

/**
 * State holder for the home screen.
 *
 * Hilt creates it and passes the app [Clock] and the other dependencies into the constructor (constructor injection).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(clock: Clock, private val speech: SpeechController, reading: BibleReading) :
    ViewModel() {
    private val today = LocalDate.now(clock)
    private val marks = ReadMarks(reading, viewModelScope)

    val uiState: StateFlow<HomeUiState> = combine(speech.state, reading.todayReading(), marks.message) {
            speech,
            read,
            message,
        ->
        HomeUiState(today, speech, read, message)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(today))

    /** Loads today's data, writes the briefing (AI first, the template as fallback) and speaks it. */
    fun play(kind: BriefingKind) = speech.speakToday(kind)

    fun stop() = speech.stop()

    /** "Přečteno": today's chapter is read. */
    fun markRead() = marks.markRead()

    fun undoRead(mark: ReadMark) = marks.undo(mark)

    fun readingMessageShown(message: ReadingMessage) = marks.shown(message)
}
