package io.github.adccz.elias.ui.scheduled

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackTrigger
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.SpeechState
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.testing.FakeVoicePlayer
import io.github.adccz.elias.testing.MainDispatcherExtension
import io.github.adccz.elias.ui.voice.SpeechController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledBriefingViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val player = FakeVoicePlayer()

    @Test
    fun `starts the scheduled evening briefing on the alarm stream`() = runTest {
        ScheduledBriefingViewModel(SpeechController(player)).start()

        assertEquals(listOf(BriefingRequest.FromToday(BriefingKind.EVENING, PlaybackTrigger.SCHEDULE)), player.requests)
    }

    @Test
    fun `leaves a briefing that is already running alone`() = runTest {
        player.state.value = SpeechState.Speaking(BriefingKind.EVENING, isPaused = false)

        ScheduledBriefingViewModel(SpeechController(player)).start()

        assertTrue(player.requests.isEmpty())
    }

    @Test
    fun `is over when the briefing ends`() = runTest {
        val viewModel = ScheduledBriefingViewModel(SpeechController(player))
        viewModel.start()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOver)

        player.state.value = SpeechState.Speaking(BriefingKind.EVENING, isPaused = false)
        advanceUntilIdle()
        player.state.value = SpeechState.Idle
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOver)
    }

    @Test
    fun `a failure stays on the screen`() = runTest {
        val viewModel = ScheduledBriefingViewModel(SpeechController(player))
        viewModel.start()
        advanceUntilIdle()

        player.state.value =
            SpeechState.Failed(BriefingKind.EVENING, SpeakResult.VoiceNotReady(VoiceStatus.EngineUnavailable))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOver)
    }
}
