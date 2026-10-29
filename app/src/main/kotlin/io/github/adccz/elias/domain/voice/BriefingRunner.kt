package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingService
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunLog
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.log.SourceRecord
import io.github.adccz.elias.domain.log.sourceRecords
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.overview.TodayOverviewLoader
import io.github.adccz.elias.domain.routine.ExerciseScript
import io.github.adccz.elias.domain.routine.ExerciseStep
import io.github.adccz.elias.domain.routine.MorningRoutine
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
 * "Cvič se mnou" (M5) takes the same path with today's exercises and their silent time, without the run log.
 * It never throws for source, AI, engine or player problems: it reports a [SpeakResult.Problem].
 */
class BriefingRunner @Inject constructor(
    private val loader: TodayOverviewLoader,
    private val briefings: BriefingService,
    private val synthesizer: SpeechSynthesizer,
    private val settings: VoiceSettingsRepository,
    private val runLog: RunLog,
    private val clock: Clock,
    private val routine: MorningRoutine,
) {
    suspend fun run(request: BriefingRequest, player: PlaylistPlayer): SpeakResult {
        if (request is BriefingRequest.Exercises) return guarded { speakExercises(player) }
        val startedAt = clock.instant()
        val notes = WritingNotes()
        val result = try {
            val briefing = when (request) {
                is BriefingRequest.Written -> request.briefing
                is BriefingRequest.FromToday -> write(request.kind, notes)
                BriefingRequest.Exercises -> error("Exercises are not a briefing")
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

    private suspend fun guarded(block: suspend () -> SpeakResult): SpeakResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SpeakResult.SynthesisFailed(e.toString())
    }

    /** "Cvič se mnou": today's exercises, each step spoken into a clip and the time to exercise as silence. */
    private suspend fun speakExercises(player: PlaylistPlayer): SpeakResult {
        val exercises = (routine.exercisesFor(routine.today()) as? SourceResult.Success)?.data.orEmpty()
        val steps = ExerciseScript.of(exercises)
        val sections = steps.filterIsInstance<ExerciseStep.Say>().map { BriefingSection(SectionKind.EXERCISE, it.text) }
        if (sections.isEmpty()) return SpeakResult.NothingToSay
        return when (val synthesis = synthesizer.synthesize(sections, settings.speechRate.first())) {
            is SynthesisResult.Success -> {
                val playlist = ExercisePlaylist.of(steps, synthesis.clips)
                if (player.play(playlist, PlaybackStream.MEDIA)) SpeakResult.Started else SpeakResult.PlaybackFailed
            }

            is SynthesisResult.VoiceNotReady -> SpeakResult.VoiceNotReady(synthesis.status)

            is SynthesisResult.Failure -> SpeakResult.SynthesisFailed(synthesis.detail)
        }
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
            BriefingKind.EXERCISES -> error("Exercises are not a briefing")
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
                BriefingKind.EXERCISES -> return
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
