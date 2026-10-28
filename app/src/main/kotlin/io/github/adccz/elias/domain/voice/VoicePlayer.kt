package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import kotlinx.coroutines.flow.StateFlow

/** The Android audio stream Elias speaks on. It decides which volume applies. */
enum class PlaybackStream {
    /** Media volume, for briefings the user starts. */
    MEDIA,

    /**
     * Alarm volume, for briefings that start on their own (from M4). Android 17 lets an app play audio from the
     * background without a user action only on the alarm stream, and only with the exact alarm permission.
     */
    ALARM,
}

/** Who started a briefing. It decides the audio stream. */
enum class PlaybackTrigger(val stream: PlaybackStream) {
    /** A tap in the app or on the notification. */
    USER(PlaybackStream.MEDIA),

    /** A scheduled briefing (M4) or the alarm clock (M6). */
    SCHEDULE(PlaybackStream.ALARM),
}

/** What the player is doing right now. */
sealed interface PlaybackState {
    data object Idle : PlaybackState

    /** A briefing is loaded. [isPaused] is true after Pause in the notification or during a phone call. */
    data class Speaking(val kind: BriefingKind, val isPaused: Boolean) : PlaybackState
}

/** Plays spoken briefings. Implemented in the data layer by a Media3 playback service. */
interface VoicePlayer {
    val state: StateFlow<PlaybackState>

    /** Plays [playlist] from the start and replaces anything that was playing. False when playback could not start. */
    suspend fun play(playlist: VoicePlaylist, stream: PlaybackStream): Boolean

    /** Stops speaking. The playback service and its notification end. */
    fun stop()
}
