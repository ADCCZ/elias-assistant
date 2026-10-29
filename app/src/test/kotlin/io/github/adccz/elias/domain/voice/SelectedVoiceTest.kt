package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.testing.FakeSpeechSynthesizer
import io.github.adccz.elias.testing.FakeVoiceChecker
import io.github.adccz.elias.testing.FakeVoiceSettings
import io.github.adccz.elias.testing.testEngine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelectedVoiceTest {
    private val sections = listOf(BriefingSection(SectionKind.CHECKLIST, "Dobrý večer."))
    private val builtInReady = VoiceStatus.Ready(TtsEngine("io.github.adccz.elias", "Oldřich 3.0"), "Jirka")

    /** The app's own voice: counts its calls and answers with [result]; [status] null means no model. */
    private class CountingBuiltIn(override val status: VoiceStatus?, private val result: SynthesisResult) :
        BuiltInVoice {
        override val crashedBefore = false
        var calls = 0

        override suspend fun synthesize(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult {
            calls++
            return result
        }
    }

    private val phone = FakeSpeechSynthesizer()
    private val phoneChecker = FakeVoiceChecker()

    private fun voice(builtIn: BuiltInVoice, choice: VoiceChoice = VoiceChoice.BUILT_IN) =
        SelectedVoice(builtIn, phone, phoneChecker, FakeVoiceSettings(choice = choice))

    private val clips = listOf(SpeechClip(SectionKind.CHECKLIST, "voice/builtin-0.wav"))

    @Test
    fun `speaks with the app's own voice by default`() = runTest {
        val builtIn = CountingBuiltIn(builtInReady, SynthesisResult.Success(clips))

        val result = voice(builtIn).synthesize(sections, SpeechRate.NORMAL)

        assertEquals(SynthesisResult.Success(clips), result)
        assertEquals(1, builtIn.calls)
        assertTrue(phone.requests.isEmpty())
    }

    @Test
    fun `falls back to the phone's voice when the app's voice fails`() = runTest {
        val builtIn = CountingBuiltIn(builtInReady, SynthesisResult.Failure("out of memory"))

        val result = voice(builtIn).synthesize(sections, SpeechRate.NORMAL)

        assertTrue(result is SynthesisResult.Success)
        assertEquals(1, phone.requests.size)
    }

    @Test
    fun `uses the phone's voice when the build has no voice model`() = runTest {
        val builtIn = CountingBuiltIn(null, SynthesisResult.Success(clips))

        voice(builtIn).synthesize(sections, SpeechRate.NORMAL)

        assertEquals(0, builtIn.calls)
        assertEquals(1, phone.requests.size)
    }

    @Test
    fun `uses the phone's voice when the user chose it`() = runTest {
        val builtIn = CountingBuiltIn(builtInReady, SynthesisResult.Success(clips))

        voice(builtIn, VoiceChoice.PHONE).synthesize(sections, SpeechRate.NORMAL)

        assertEquals(0, builtIn.calls)
        assertEquals(1, phone.requests.size)
    }

    @Test
    fun `the voice check reports the voice in use`() = runTest {
        val builtIn = CountingBuiltIn(builtInReady, SynthesisResult.Success(clips))

        assertEquals(builtInReady, voice(builtIn).check())
        assertEquals(VoiceStatus.Ready(testEngine, "cs-local"), voice(builtIn, VoiceChoice.PHONE).check())
        assertEquals(
            VoiceStatus.Ready(testEngine, "cs-local"),
            voice(CountingBuiltIn(null, SynthesisResult.Success(clips))).check(),
        )
    }
}
