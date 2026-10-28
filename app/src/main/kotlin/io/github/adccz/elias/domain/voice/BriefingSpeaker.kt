package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingText
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
 * Speaks a briefing: each section becomes an audio clip at the stored speech rate, and the clips go to the player
 * with short pauses in between. Never throws for engine or player problems, it reports a [SpeakResult.Problem].
 */
class BriefingSpeaker @Inject constructor(
    private val synthesizer: SpeechSynthesizer,
    private val player: VoicePlayer,
    private val settings: VoiceSettingsRepository,
) {
    suspend fun speak(briefing: BriefingText, trigger: PlaybackTrigger = PlaybackTrigger.USER): SpeakResult {
        val sections = briefing.spokenSections
        if (sections.isEmpty()) return SpeakResult.NothingToSay
        // A new briefing replaces the one being spoken, whose clips are about to be overwritten.
        player.stop()
        return try {
            when (val synthesis = synthesizer.synthesize(sections, settings.speechRate.first())) {
                is SynthesisResult.Success -> {
                    val playlist = BriefingPlaylist.of(briefing.kind, synthesis.clips)
                    if (player.play(playlist, trigger.stream)) SpeakResult.Started else SpeakResult.PlaybackFailed
                }

                is SynthesisResult.VoiceNotReady -> SpeakResult.VoiceNotReady(synthesis.status)

                is SynthesisResult.Failure -> SpeakResult.SynthesisFailed(synthesis.detail)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A bug in the engine wrapper must not crash the app: the briefing is still on the screen.
            SpeakResult.SynthesisFailed(e.toString())
        }
    }
}
