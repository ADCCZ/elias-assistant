package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunLog
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.log.SourceRecord
import io.github.adccz.elias.domain.log.sourceRecords
import io.github.adccz.elias.domain.overview.TodayOverviewLoader
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** What happened when Elias was asked to speak a briefing. */
sealed interface SpeakResult {
    data object Started : SpeakResult

    /** Elias does not speak. The screen tells the user why. */
    sealed interface Problem : SpeakResult

    data object NothingToSay : Problem

    data class VoiceNotReady(val status: VoiceStatus) : Problem

    /** [detail] is technical and never shown as is. */
    data class SynthesisFailed(val detail: String) : Problem

    data object PlaybackFailed : Problem
}

/**
 * Runs one briefing from the request to the first word: writes it from today's data when needed (the AI first,
 * the template as fallback), speaks each section into a clip at the stored speech rate, hands the clips with short
 * pauses to the player and records the run in the log.
 *
 * The playback service calls it, so it works the same for a tap and for a scheduled alarm, with no screen open.
 * It never throws for source, AI, engine or player problems: it reports a [SpeakResult.Problem].
 */
class BriefingRunner @Inject constructor(
    private val loader: TodayOverviewLoader,
    private val briefings: BriefingService,
    private val synthesizer: SpeechSynthesizer,
    private val settings: VoiceSettingsRepository,
    private val runLog: RunLog,
    private val clock: Clock,
) {
    suspend fun run(request: BriefingRequest, player: PlaylistPlayer): SpeakResult {
        val startedAt = clock.instant()
        val notes = WritingNotes()
        val result = try {
            val briefing = when (request) {
                is BriefingRequest.Written -> request.briefing
                is BriefingRequest.FromToday -> write(request.kind, notes)
            }
            speak(briefing, request.trigger.stream, player)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A bug in a source, the AI or the engine wrapper must not crash the playback service.
            SpeakResult.SynthesisFailed(e.toString())
        }
        record(request, startedAt, result, notes)
        return result
    }

    /** What the log learns while the briefing is written. */
    private class WritingNotes(
        var sources: List<SourceRecord> = emptyList(),
        var writtenBy: BriefingSource? = null,
        var aiFailure: AiFailureReason? = null,
    )

    private suspend fun write(kind: BriefingKind, notes: WritingNotes): BriefingText {
        val overview = loader.load()
        notes.sources = overview.sourceRecords()
        val result = when (kind) {
            BriefingKind.MORNING -> briefings.morning(overview)
            BriefingKind.EVENING -> briefings.evening(overview)
        }
        notes.writtenBy = result.source
        notes.aiFailure = result.aiFailure?.reason
        return result.briefing
    }

    private suspend fun speak(briefing: BriefingText, stream: PlaybackStream, player: PlaylistPlayer): SpeakResult {
        val sections = briefing.spokenSections
        if (sections.isEmpty()) return SpeakResult.NothingToSay
        return when (val synthesis = synthesizer.synthesize(sections, settings.speechRate.first())) {
            is SynthesisResult.Success -> {
                val playlist = BriefingPlaylist.of(briefing.kind, synthesis.clips)
                if (player.play(playlist, stream)) SpeakResult.Started else SpeakResult.PlaybackFailed
            }

            is SynthesisResult.VoiceNotReady -> SpeakResult.VoiceNotReady(synthesis.status)

            is SynthesisResult.Failure -> SpeakResult.SynthesisFailed(synthesis.detail)
        }
    }

    private suspend fun record(request: BriefingRequest, startedAt: Instant, result: SpeakResult, notes: WritingNotes) {
        val run = RunRecord(
            kind = when (request.kind) {
                BriefingKind.MORNING -> RunKind.MORNING_BRIEFING
                BriefingKind.EVENING -> RunKind.EVENING_BRIEFING
            },
            trigger = when (request.trigger) {
                PlaybackTrigger.USER -> RunTrigger.USER
                PlaybackTrigger.SCHEDULE -> RunTrigger.SCHEDULE
            },
            startedAt = startedAt,
            finishedAt = clock.instant(),
            outcome = when (result) {
                SpeakResult.Started -> RunOutcome.PLAYED
                SpeakResult.NothingToSay -> RunOutcome.NOTHING_TO_SAY
                is SpeakResult.VoiceNotReady -> RunOutcome.VOICE_NOT_READY
                is SpeakResult.SynthesisFailed -> RunOutcome.SYNTHESIS_FAILED
                SpeakResult.PlaybackFailed -> RunOutcome.PLAYBACK_FAILED
            },
            sources = notes.sources,
            writtenBy = notes.writtenBy,
            aiFailure = notes.aiFailure,
        )
        try {
            runLog.record(run)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The log is for looking back. A broken database must not cost the user their briefing.
        }
    }
}
