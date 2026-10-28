package io.github.adccz.elias.ui.home

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.briefing.AiBriefingBuilder
import io.github.adccz.elias.domain.briefing.AiBriefingResult
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.briefing.EveningInput
import io.github.adccz.elias.domain.briefing.MorningInput
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.briefing.TemplateBriefingBuilder
import io.github.adccz.elias.domain.voice.BriefingSpeaker
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.SynthesisResult
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.testing.FakeSpeechSynthesizer
import io.github.adccz.elias.testing.FakeVoicePlayer
import io.github.adccz.elias.testing.FakeVoiceSettings
import io.github.adccz.elias.testing.MainDispatcherExtension
import io.github.adccz.elias.testing.TestLoaders
import io.github.adccz.elias.ui.voice.SpeechController
import io.github.adccz.elias.ui.voice.SpeechUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val synthesizer = FakeSpeechSynthesizer()
    private val player = FakeVoicePlayer()

    private val noAi = object : AiBriefingBuilder {
        override suspend fun morning(input: MorningInput) = AiBriefingResult.Failure(AiFailureReason.NO_API_KEY)

        override suspend fun evening(input: EveningInput) = AiBriefingResult.Failure(AiFailureReason.NO_API_KEY)
    }

    private fun viewModelAt(instant: String): HomeViewModel {
        val clock = Clock.fixed(Instant.parse(instant), PRAGUE_ZONE)
        return HomeViewModel(
            clock = clock,
            loader = TestLoaders.failing(clock),
            briefings = BriefingService(TemplateBriefingBuilder(), noAi),
            speech = SpeechController(BriefingSpeaker(synthesizer, player, FakeVoiceSettings()), player),
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
    fun `playing the evening briefing writes it and speaks it`() = runTest {
        val viewModel = viewModelAt("2026-10-27T17:00:00Z")

        viewModel.play(BriefingKind.EVENING)
        advanceUntilIdle()

        assertEquals(SpeechUiState.Speaking(BriefingKind.EVENING, isPaused = false), viewModel.uiState.value.speech)
        val (sections, _) = synthesizer.requests.single()
        assertEquals(SectionKind.CHECKLIST, sections.first().kind)
        assertEquals(BriefingKind.EVENING, player.played.single().first.kind)
    }

    @Test
    fun `shows that it is preparing until the speech is ready`() = runTest {
        synthesizer.gate = CompletableDeferred()
        val viewModel = viewModelAt("2026-10-27T06:00:00Z")

        viewModel.play(BriefingKind.MORNING)
        advanceUntilIdle()
        assertEquals(SpeechUiState.Preparing(BriefingKind.MORNING), viewModel.uiState.value.speech)

        synthesizer.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(SpeechUiState.Speaking(BriefingKind.MORNING, isPaused = false), viewModel.uiState.value.speech)
    }

    @Test
    fun `a missing Czech voice is shown instead of speaking`() = runTest {
        val status = VoiceStatus.EngineUnavailable
        synthesizer.result = SynthesisResult.VoiceNotReady(status)
        val viewModel = viewModelAt("2026-10-27T06:00:00Z")

        viewModel.play(BriefingKind.MORNING)
        advanceUntilIdle()

        val expected = SpeechUiState.Failed(BriefingKind.MORNING, SpeakResult.VoiceNotReady(status))
        assertEquals(expected, viewModel.uiState.value.speech)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `stop while preparing speaks nothing`() = runTest {
        synthesizer.gate = CompletableDeferred()
        val viewModel = viewModelAt("2026-10-27T06:00:00Z")
        viewModel.play(BriefingKind.MORNING)
        advanceUntilIdle()

        viewModel.stop()
        synthesizer.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(SpeechUiState.Idle, viewModel.uiState.value.speech)
        assertTrue(player.played.isEmpty())
    }
}
