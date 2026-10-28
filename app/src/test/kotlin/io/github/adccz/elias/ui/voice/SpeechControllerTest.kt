package io.github.adccz.elias.ui.voice

import app.cash.turbine.test
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.voice.BriefingSpeaker
import io.github.adccz.elias.domain.voice.PlaybackState
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.SynthesisResult
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.testing.FakeSpeechSynthesizer
import io.github.adccz.elias.testing.FakeVoicePlayer
import io.github.adccz.elias.testing.FakeVoiceSettings
import io.github.adccz.elias.testing.testEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechControllerTest {
    private val synthesizer = FakeSpeechSynthesizer()
    private val player = FakeVoicePlayer()
    private val controller = SpeechController(BriefingSpeaker(synthesizer, player, FakeVoiceSettings()), player)

    private val morning = BriefingText(
        BriefingKind.MORNING,
        listOf(BriefingSection(SectionKind.GREETING_AND_WEATHER, "Dobré ráno.")),
    )

    @Test
    fun `shows preparing and then speaking`() = runTest {
        synthesizer.gate = CompletableDeferred()
        controller.state.test {
            assertEquals(SpeechUiState.Idle, awaitItem())

            val speaking = launch { controller.speak(BriefingKind.MORNING) { morning } }
            assertEquals(SpeechUiState.Preparing(BriefingKind.MORNING), awaitItem())

            synthesizer.gate?.complete(Unit)
            speaking.join()
            assertEquals(SpeechUiState.Speaking(BriefingKind.MORNING, isPaused = false), expectMostRecentItem())
        }
    }

    @Test
    fun `follows a pause from the notification`() = runTest {
        controller.speak(BriefingKind.MORNING) { morning }

        player.state.value = PlaybackState.Speaking(BriefingKind.MORNING, isPaused = true)

        controller.state.test {
            assertEquals(SpeechUiState.Speaking(BriefingKind.MORNING, isPaused = true), awaitItem())
        }
    }

    @Test
    fun `shows why Elias does not speak`() = runTest {
        synthesizer.result = SynthesisResult.VoiceNotReady(VoiceStatus.NoCzech(testEngine))

        controller.speak(BriefingKind.EVENING) { morning }

        controller.state.test {
            val expected = SpeakResult.VoiceNotReady(VoiceStatus.NoCzech(testEngine))
            assertEquals(SpeechUiState.Failed(BriefingKind.EVENING, expected), awaitItem())
        }
    }

    @Test
    fun `cancelling while preparing goes back to idle`() = runTest {
        synthesizer.gate = CompletableDeferred()
        val speaking = launch { controller.speak(BriefingKind.MORNING) { morning } }
        testScheduler.runCurrent()

        speaking.cancel()
        speaking.join()

        controller.state.test {
            assertEquals(SpeechUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `stop ends speaking and clears a failure`() = runTest {
        player.accepts = false
        controller.speak(BriefingKind.MORNING) { morning }

        controller.stop()

        controller.state.test {
            assertEquals(SpeechUiState.Idle, awaitItem())
        }
    }
}
