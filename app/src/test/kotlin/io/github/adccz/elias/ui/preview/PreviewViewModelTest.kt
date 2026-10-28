package io.github.adccz.elias.ui.preview

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.briefing.AiBriefingBuilder
import io.github.adccz.elias.domain.briefing.AiBriefingResult
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.briefing.EveningInput
import io.github.adccz.elias.domain.briefing.MorningInput
import io.github.adccz.elias.domain.briefing.TemplateBriefingBuilder
import io.github.adccz.elias.domain.voice.BriefingSpeaker
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.testing.FakeSpeechSynthesizer
import io.github.adccz.elias.testing.FakeVoiceChecker
import io.github.adccz.elias.testing.FakeVoicePlayer
import io.github.adccz.elias.testing.FakeVoiceSettings
import io.github.adccz.elias.testing.MainDispatcherExtension
import io.github.adccz.elias.testing.TestLoaders
import io.github.adccz.elias.testing.testEngine
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = Clock.fixed(Instant.parse("2026-10-27T06:00:00Z"), PRAGUE_ZONE)

    /** Every data source fails: the test is about the screen logic, not the data. */
    private val loader = TestLoaders.failing(clock)

    private class NoCreditAi : AiBriefingBuilder {
        var calls = 0

        override suspend fun morning(input: MorningInput): AiBriefingResult {
            calls++
            return AiBriefingResult.Failure(AiFailureReason.NO_CREDIT)
        }

        override suspend fun evening(input: EveningInput): AiBriefingResult {
            calls++
            return AiBriefingResult.Failure(AiFailureReason.NO_CREDIT)
        }
    }

    private val synthesizer = FakeSpeechSynthesizer()
    private val player = FakeVoicePlayer()
    private val settings = FakeVoiceSettings()
    private val checker = FakeVoiceChecker()

    private fun viewModel(ai: AiBriefingBuilder = NoCreditAi()) = PreviewViewModel(
        loader = loader,
        briefings = BriefingService(TemplateBriefingBuilder(), ai),
        speech = SpeechController(BriefingSpeaker(synthesizer, player, settings), player),
        voiceChecker = checker,
        voiceSettings = settings,
    )

    @Test
    fun `writes both briefings and explains why the template was used`() = runTest {
        val ai = NoCreditAi()
        val viewModel = viewModel(ai)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(BriefingSource.TEMPLATE, state.morning?.source)
        assertEquals(AiFailureReason.NO_CREDIT, state.evening?.aiFailure?.reason)
        assertEquals(2, ai.calls)
    }

    @Test
    fun `switching AI off writes from the template without asking the AI`() = runTest {
        val ai = NoCreditAi()
        val viewModel = viewModel(ai)
        advanceUntilIdle()

        viewModel.setUseAi(false)
        advanceUntilIdle()

        assertEquals(AiFailureReason.DISABLED, viewModel.uiState.value.morning?.aiFailure?.reason)
        assertEquals(2, ai.calls) // only the first generation asked the AI
    }

    @Test
    fun `the selected tab decides which briefing is shown`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.select(BriefingKind.EVENING)

        assertEquals(BriefingKind.EVENING, viewModel.uiState.value.current?.briefing?.kind)
    }

    @Test
    fun `plays exactly the briefing on the screen`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.select(BriefingKind.EVENING)

        viewModel.play()
        advanceUntilIdle()

        val shown = viewModel.uiState.value.evening!!.briefing.spokenSections
        assertEquals(shown, synthesizer.requests.single().first)
        assertEquals(SpeechUiState.Speaking(BriefingKind.EVENING, isPaused = false), viewModel.uiState.value.speech)
    }

    @Test
    fun `stop ends speaking`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.play()
        advanceUntilIdle()

        viewModel.stop()
        advanceUntilIdle()

        assertEquals(SpeechUiState.Idle, viewModel.uiState.value.speech)
    }

    @Test
    fun `the speech rate is saved when the slider is let go`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSpeechRateChange(1.27f)
        assertEquals(SpeechRate.NORMAL, settings.speechRate.value) // not saved while dragging
        viewModel.onSpeechRateChangeFinished()
        advanceUntilIdle()

        assertEquals(SpeechRate.of(1.3f), settings.speechRate.value)
        assertEquals(SpeechRate.of(1.3f), viewModel.uiState.value.speechRate)
    }

    @Test
    fun `shows the stored speech rate`() = runTest {
        settings.speechRate.value = SpeechRate.SLOWEST
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(SpeechRate.SLOWEST, viewModel.uiState.value.speechRate)
    }

    @Test
    fun `checks the Czech voice and again after a fix in the settings`() = runTest {
        checker.status = VoiceStatus.NotDownloaded(testEngine)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(VoiceStatus.NotDownloaded(testEngine), viewModel.uiState.value.voice)

        checker.status = VoiceStatus.Ready(testEngine, "cs-local")
        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertEquals(VoiceStatus.Ready(testEngine, "cs-local"), viewModel.uiState.value.voice)
        assertEquals(2, checker.checks)
    }

    @Test
    fun `a ready voice is not checked again on every return to the screen`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertEquals(1, checker.checks)
    }
}
