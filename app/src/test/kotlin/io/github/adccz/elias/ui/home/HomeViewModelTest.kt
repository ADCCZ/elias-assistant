package io.github.adccz.elias.ui.home

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.reading.BibleReading
import io.github.adccz.elias.domain.reading.ReadingQueue
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackTrigger
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.SpeechState
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.testing.FakeVoicePlayer
import io.github.adccz.elias.testing.FixedPlans
import io.github.adccz.elias.testing.InMemoryReadingLog
import io.github.adccz.elias.testing.InMemoryReadingQueue
import io.github.adccz.elias.testing.MainDispatcherExtension
import io.github.adccz.elias.ui.reading.ReadingMessage
import io.github.adccz.elias.ui.reading.TodayReading
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val player = FakeVoicePlayer()
    private val mark = BibleBooks.byAbbreviation("Mk")!!
    private val queue = InMemoryReadingQueue(ReadingQueue().add(mark, 3, 16))

    private fun viewModelAt(instant: String): HomeViewModel {
        val clock = Clock.fixed(Instant.parse(instant), PRAGUE_ZONE)
        return HomeViewModel(
            clock = clock,
            speech = SpeechController(player),
            reading = BibleReading(queue, InMemoryReadingLog(), FixedPlans(null), clock),
        )
    }

    @Test
    fun `today is the current date in Prague`() {
        val viewModel = viewModelAt("2026-10-27T09:00:00Z")

        assertEquals(LocalDate.of(2026, 10, 27), viewModel.uiState.value.today)
    }

    @Test
    fun `date changes at Prague midnight, not at UTC midnight`() {
        // 23:30 UTC on 27 October is already 00:30 on 28 October in Prague (winter time, UTC+1).
        val viewModel = viewModelAt("2026-10-27T23:30:00Z")

        assertEquals(LocalDate.of(2026, 10, 28), viewModel.uiState.value.today)
    }

    @Test
    fun `playing the evening briefing asks for today's evening briefing and shows it is preparing`() = runTest {
        val viewModel = viewModelAt("2026-10-27T17:00:00Z")

        viewModel.play(BriefingKind.EVENING)
        advanceUntilIdle()

        assertEquals(listOf(BriefingRequest.FromToday(BriefingKind.EVENING, PlaybackTrigger.USER)), player.requests)
        assertEquals(SpeechUiState.Preparing(BriefingKind.EVENING), viewModel.uiState.value.speech)
    }

    @Test
    fun `shows what the playback service is doing`() = runTest {
        val viewModel = viewModelAt("2026-10-27T06:00:00Z")

        player.state.value = SpeechState.Speaking(BriefingKind.MORNING, isPaused = true)
        advanceUntilIdle()

        assertEquals(SpeechUiState.Speaking(BriefingKind.MORNING, isPaused = true), viewModel.uiState.value.speech)
    }

    @Test
    fun `a missing Czech voice is shown instead of speaking`() = runTest {
        val viewModel = viewModelAt("2026-10-27T06:00:00Z")
        val problem = SpeakResult.VoiceNotReady(VoiceStatus.EngineUnavailable)

        player.state.value = SpeechState.Failed(BriefingKind.MORNING, problem)
        advanceUntilIdle()

        assertEquals(SpeechUiState.Failed(BriefingKind.MORNING, problem), viewModel.uiState.value.speech)
    }

    @Test
    fun `stop asks the playback service to stop`() = runTest {
        val viewModel = viewModelAt("2026-10-27T06:00:00Z")
        viewModel.play(BriefingKind.MORNING)

        viewModel.stop()
        advanceUntilIdle()

        assertEquals(1, player.stops)
        assertEquals(SpeechUiState.Idle, viewModel.uiState.value.speech)
    }

    @Test
    fun `shows today's chapter and marks it as read with a way back`() = runTest {
        val viewModel = viewModelAt("2026-10-27T06:00:00Z")
        advanceUntilIdle()
        assertEquals(TodayReading(next = Chapter(mark, 3)), viewModel.uiState.value.reading)

        viewModel.markRead()
        advanceUntilIdle()

        assertEquals(TodayReading(Chapter(mark, 4), listOf(Chapter(mark, 3))), viewModel.uiState.value.reading)
        val message = viewModel.uiState.value.readingMessage as ReadingMessage.Marked
        viewModel.undoRead(message.mark)
        viewModel.readingMessageShown(message)
        advanceUntilIdle()

        assertEquals(TodayReading(next = Chapter(mark, 3)), viewModel.uiState.value.reading)
        assertEquals(null, viewModel.uiState.value.readingMessage)
    }
}
