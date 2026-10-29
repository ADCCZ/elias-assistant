package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.briefing.AiBriefingBuilder
import io.github.adccz.elias.domain.briefing.AiBriefingResult
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.EveningInput
import io.github.adccz.elias.domain.briefing.MorningInput
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.briefing.TemplateBriefingBuilder
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.log.SourceOutcome
import io.github.adccz.elias.testing.FakePlaylistPlayer
import io.github.adccz.elias.testing.FakeRunLog
import io.github.adccz.elias.testing.FakeSpeechSynthesizer
import io.github.adccz.elias.testing.FakeVoiceSettings
import io.github.adccz.elias.testing.TestLoaders
import io.github.adccz.elias.testing.testEngine
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BriefingRunnerTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-28T21:00:00Z"), PRAGUE_ZONE)
    private val synthesizer = FakeSpeechSynthesizer()
    private val player = FakePlaylistPlayer()
    private val settings = FakeVoiceSettings(SpeechRate.of(1.2f))
    private val runLog = FakeRunLog()

    private val noAi = object : AiBriefingBuilder {
        override suspend fun morning(input: MorningInput) = AiBriefingResult.Failure(AiFailureReason.NO_CREDIT)

        override suspend fun evening(input: EveningInput) = AiBriefingResult.Failure(AiFailureReason.NO_CREDIT)
    }

    private fun runner(speech: SpeechSynthesizer = synthesizer, log: FakeRunLog = runLog) = BriefingRunner(
        loader = TestLoaders.failing(clock),
        briefings = BriefingService(TemplateBriefingBuilder(), noAi),
        synthesizer = speech,
        settings = settings,
        runLog = log,
        clock = clock,
    )

    private val evening = BriefingText(
        BriefingKind.EVENING,
        listOf(
            BriefingSection(SectionKind.TOMORROW, "Zítra v devět máš seminář."),
            BriefingSection(SectionKind.CHECKLIST, "Odškrtni si dnešek."),
            BriefingSection(SectionKind.REFLECTION, " "),
        ),
    )

    private suspend fun speakWritten(briefing: BriefingText = evening) =
        runner().run(BriefingRequest.Written(briefing), player)

    @Test
    fun `speaks the sections in order at the stored rate`() = runTest {
        val result = speakWritten()

        assertEquals(SpeakResult.Started, result)
        val (sections, rate) = synthesizer.requests.single()
        assertEquals(listOf(SectionKind.CHECKLIST, SectionKind.TOMORROW), sections.map { it.kind })
        assertEquals(SpeechRate.of(1.2f), rate)
    }

    @Test
    fun `plays the clips with a pause between them on the media stream`() = runTest {
        speakWritten()

        val (playlist, stream) = player.played.single()
        assertEquals(BriefingKind.EVENING, playlist.kind)
        assertEquals(3, playlist.entries.size)
        assertTrue(playlist.entries[1] is PlaylistEntry.Pause)
        assertEquals(PlaybackStream.MEDIA, stream)
    }

    @Test
    fun `writes a scheduled briefing from today's data and plays it on the alarm stream`() = runTest {
        val result = runner().run(BriefingRequest.FromToday(BriefingKind.EVENING, PlaybackTrigger.SCHEDULE), player)

        assertEquals(SpeakResult.Started, result)
        val (playlist, stream) = player.played.single()
        assertEquals(BriefingKind.EVENING, playlist.kind)
        assertEquals(PlaybackStream.ALARM, stream)
    }

    @Test
    fun `records a briefing written from today's data`() = runTest {
        runner().run(BriefingRequest.FromToday(BriefingKind.MORNING, PlaybackTrigger.USER), player)

        val run = runLog.runs.single()
        assertEquals(RunKind.MORNING_BRIEFING, run.kind)
        assertEquals(RunTrigger.USER, run.trigger)
        assertEquals(RunOutcome.PLAYED, run.outcome)
        assertEquals(clock.instant(), run.startedAt)
        assertEquals(BriefingSource.TEMPLATE, run.writtenBy)
        assertEquals(AiFailureReason.NO_CREDIT, run.aiFailure)
        // Every source fails in TestLoaders.failing.
        assertTrue(run.sources.isNotEmpty())
        assertTrue(run.sources.all { it.outcome == SourceOutcome.FAILED })
    }

    @Test
    fun `records the text from the preview without sources`() = runTest {
        speakWritten()

        val run = runLog.runs.single()
        assertEquals(RunKind.EVENING_BRIEFING, run.kind)
        assertEquals(RunOutcome.PLAYED, run.outcome)
        assertTrue(run.sources.isEmpty())
        assertEquals(null, run.writtenBy)
    }

    @Test
    fun `an empty briefing is not spoken`() = runTest {
        val empty = BriefingText(BriefingKind.MORNING, listOf(BriefingSection(SectionKind.GREETING_AND_WEATHER, "")))

        assertEquals(SpeakResult.NothingToSay, speakWritten(empty))
        assertTrue(synthesizer.requests.isEmpty())
        assertEquals(RunOutcome.NOTHING_TO_SAY, runLog.runs.single().outcome)
    }

    @Test
    fun `a missing voice is reported and nothing is played`() = runTest {
        synthesizer.result = SynthesisResult.VoiceNotReady(VoiceStatus.NoCzech(testEngine))

        assertEquals(SpeakResult.VoiceNotReady(VoiceStatus.NoCzech(testEngine)), speakWritten())
        assertTrue(player.played.isEmpty())
        assertEquals(RunOutcome.VOICE_NOT_READY, runLog.runs.single().outcome)
    }

    @Test
    fun `an engine failure is reported`() = runTest {
        synthesizer.result = SynthesisResult.Failure("No audio for CHECKLIST")

        assertEquals(SpeakResult.SynthesisFailed("No audio for CHECKLIST"), speakWritten())
    }

    @Test
    fun `a player that does not start is reported`() = runTest {
        player.accepts = false

        assertEquals(SpeakResult.PlaybackFailed, speakWritten())
        assertEquals(RunOutcome.PLAYBACK_FAILED, runLog.runs.single().outcome)
    }

    @Test
    fun `a crashing synthesizer does not crash the service`() = runTest {
        val crashing = object : SpeechSynthesizer {
            override suspend fun synthesize(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult =
                error("bug")
        }

        val result = runner(speech = crashing).run(BriefingRequest.Written(evening), player)

        assertTrue(result is SpeakResult.SynthesisFailed)
    }

    @Test
    fun `a broken log does not cost the briefing`() = runTest {
        val result = runner(log = FakeRunLog(failing = true)).run(BriefingRequest.Written(evening), player)

        assertEquals(SpeakResult.Started, result)
        assertEquals(1, player.played.size)
    }
}
