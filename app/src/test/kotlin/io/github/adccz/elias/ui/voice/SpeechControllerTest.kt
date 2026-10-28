package io.github.adccz.elias.ui.voice

import app.cash.turbine.test
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackTrigger
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.SpeechState
import io.github.adccz.elias.domain.voice.VoiceStatus
import io.github.adccz.elias.testing.FakeVoicePlayer
import io.github.adccz.elias.testing.testEngine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeechControllerTest {
    private val player = FakeVoicePlayer()
    private val controller = SpeechController(player)

    private val morning = BriefingText(
        BriefingKind.MORNING,
        listOf(BriefingSection(SectionKind.GREETING_AND_WEATHER, "Dobré ráno.")),
    )

    @Test
    fun `follows every state of the playback service`() = runTest {
        val problem = SpeakResult.VoiceNotReady(VoiceStatus.NoCzech(testEngine))
        controller.state.test {
            assertEquals(SpeechUiState.Idle, awaitItem())

            player.state.value = SpeechState.Preparing(BriefingKind.EVENING)
            assertEquals(SpeechUiState.Preparing(BriefingKind.EVENING), awaitItem())

            player.state.value = SpeechState.Speaking(BriefingKind.EVENING, isPaused = true)
            assertEquals(SpeechUiState.Speaking(BriefingKind.EVENING, isPaused = true), awaitItem())

            player.state.value = SpeechState.Failed(BriefingKind.EVENING, problem)
            assertEquals(SpeechUiState.Failed(BriefingKind.EVENING, problem), awaitItem())
        }
    }

    @Test
    fun `asks for today's briefing as the user`() {
        controller.speakToday(BriefingKind.MORNING)

        assertEquals(BriefingRequest.FromToday(BriefingKind.MORNING, PlaybackTrigger.USER), player.requests.single())
    }

    @Test
    fun `asks for exactly the written briefing`() {
        controller.speak(morning)

        assertEquals(BriefingRequest.Written(morning), player.requests.single())
    }

    @Test
    fun `stop goes to the playback service`() {
        controller.stop()

        assertEquals(1, player.stops)
    }
}
