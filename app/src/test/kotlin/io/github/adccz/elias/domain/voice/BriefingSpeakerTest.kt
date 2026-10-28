package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.testing.FakeSpeechSynthesizer
import io.github.adccz.elias.testing.FakeVoicePlayer
import io.github.adccz.elias.testing.FakeVoiceSettings
import io.github.adccz.elias.testing.testEngine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BriefingSpeakerTest {
    private val synthesizer = FakeSpeechSynthesizer()
    private val player = FakeVoicePlayer()
    private val settings = FakeVoiceSettings(SpeechRate.of(1.2f))
    private val speaker = BriefingSpeaker(synthesizer, player, settings)

    private val evening = BriefingText(
        BriefingKind.EVENING,
        listOf(
            BriefingSection(SectionKind.TOMORROW, "Zítra v devět máš seminář."),
            BriefingSection(SectionKind.CHECKLIST, "Odškrtni si dnešek."),
            BriefingSection(SectionKind.REFLECTION, " "),
        ),
    )

    @Test
    fun `speaks the sections in order at the stored rate`() = runTest {
        val result = speaker.speak(evening)

        assertEquals(SpeakResult.Started, result)
        val (sections, rate) = synthesizer.requests.single()
        assertEquals(listOf(SectionKind.CHECKLIST, SectionKind.TOMORROW), sections.map { it.kind })
        assertEquals(SpeechRate.of(1.2f), rate)
    }

    @Test
    fun `plays the clips with a pause between them on the media stream`() = runTest {
        speaker.speak(evening)

        val (playlist, stream) = player.played.single()
        assertEquals(BriefingKind.EVENING, playlist.kind)
        assertEquals(3, playlist.entries.size)
        assertTrue(playlist.entries[1] is PlaylistEntry.Pause)
        assertEquals(PlaybackStream.MEDIA, stream)
    }

    @Test
    fun `a scheduled briefing plays on the alarm stream`() = runTest {
        speaker.speak(evening, PlaybackTrigger.SCHEDULE)

        assertEquals(PlaybackStream.ALARM, player.played.single().second)
    }

    @Test
    fun `stops the briefing being spoken before writing new clips`() = runTest {
        speaker.speak(evening)

        assertEquals(1, player.stops)
    }

    @Test
    fun `an empty briefing is not spoken`() = runTest {
        val empty = BriefingText(BriefingKind.MORNING, listOf(BriefingSection(SectionKind.GREETING_AND_WEATHER, "")))

        assertEquals(SpeakResult.NothingToSay, speaker.speak(empty))
        assertTrue(synthesizer.requests.isEmpty())
    }

    @Test
    fun `a missing voice is reported and nothing is played`() = runTest {
        synthesizer.result = SynthesisResult.VoiceNotReady(VoiceStatus.NoCzech(testEngine))

        assertEquals(SpeakResult.VoiceNotReady(VoiceStatus.NoCzech(testEngine)), speaker.speak(evening))
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `an engine failure is reported`() = runTest {
        synthesizer.result = SynthesisResult.Failure("No audio for CHECKLIST")

        assertEquals(SpeakResult.SynthesisFailed("No audio for CHECKLIST"), speaker.speak(evening))
    }

    @Test
    fun `a player that does not start is reported`() = runTest {
        player.accepts = false

        assertEquals(SpeakResult.PlaybackFailed, speaker.speak(evening))
    }

    @Test
    fun `a crashing synthesizer does not crash the app`() = runTest {
        val crashing = object : SpeechSynthesizer {
            override suspend fun synthesize(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult =
                error("bug")
        }

        val result = BriefingSpeaker(crashing, player, settings).speak(evening)

        assertTrue(result is SpeakResult.SynthesisFailed)
    }
}
