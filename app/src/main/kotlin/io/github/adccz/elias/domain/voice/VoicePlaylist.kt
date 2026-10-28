package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.SectionKind

/** One spoken section, already turned into an audio file on the phone. */
data class SpeechClip(val section: SectionKind, val path: String)

/** One item the player plays: a section's clip or a short silence between two sections. */
sealed interface PlaylistEntry {
    data class Speech(val clip: SpeechClip) : PlaylistEntry

    data class Pause(val millis: Long) : PlaylistEntry
}

/** A briefing ready to be played. One clip per section lets pause and "Znovu" work on whole sections. */
data class VoicePlaylist(val kind: BriefingKind, val entries: List<PlaylistEntry>)

object BriefingPlaylist {
    /** Silence between two sections: long enough to hear that a new part begins, too short to sound like the end. */
    const val PAUSE_MILLIS = 700L

    /** The clips in the given order with a pause between each two of them. No pause at the start or the end. */
    fun of(kind: BriefingKind, clips: List<SpeechClip>, pauseMillis: Long = PAUSE_MILLIS): VoicePlaylist {
        val entries = clips.flatMapIndexed { index, clip ->
            val speech = PlaylistEntry.Speech(clip)
            if (index == 0) listOf(speech) else listOf(PlaylistEntry.Pause(pauseMillis), speech)
        }
        return VoicePlaylist(kind, entries)
    }
}
