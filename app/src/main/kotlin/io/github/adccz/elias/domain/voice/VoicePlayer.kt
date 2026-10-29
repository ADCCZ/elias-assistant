package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingText
import kotlinx.coroutines.flow.StateFlow

/** The Android audio stream Elias speaks on. It decides which volume applies. */
enum class PlaybackStream {
    /** Media volume, for briefings the user starts. */
    MEDIA,

    /**
     * Alarm volume, for briefings that start on their own. Android 17 lets an app play audio from the background
     * without a user action only on the alarm stream, and only with the exact alarm permission.
     */
    ALARM,
}

/** Who started a briefing. It decides the audio stream. */
enum class PlaybackTrigger(val stream: PlaybackStream) {
    /** A tap in the app or on the notification. */
    USER(PlaybackStream.MEDIA),

    /** A scheduled briefing (M4) or the alarm clock (M7). */
    SCHEDULE(PlaybackStream.ALARM),
}

/** What Elias is asked to say. */
sealed interface BriefingRequest {
    val kind: BriefingKind
    val trigger: PlaybackTrigger

    /** Write the briefing from today's data (the AI first, the template as fallback) and speak it. */
    data class FromToday(override val kind: BriefingKind, override val trigger: PlaybackTrigger) : BriefingRequest

    /** Speak exactly this text, as it is shown on Náhled textu. Only the user asks for this. */
    data class Written(val briefing: BriefingText) : BriefingRequest {
        override val kind: BriefingKind get() = briefing.kind
        override val trigger: PlaybackTrigger get() = PlaybackTrigger.USER
    }
}

/** What Elias is doing with its voice right now. Every screen shows the same state. */
sealed interface SpeechState {
    data object Idle : SpeechState

    /** Loading data, writing the briefing and turning it into speech, which takes a few seconds. */
    data class Preparing(val kind: BriefingKind) : SpeechState

    /** A briefing is loaded. [isPaused] is true after Pause in the notification or during a phone call. */
    data class Speaking(val kind: BriefingKind, val isPaused: Boolean) : SpeechState

    /** The last request ended without speaking, and [problem] says why. Stays until the next request. */
    data class Failed(val kind: BriefingKind, val problem: SpeakResult.Problem) : SpeechState
}

/**
 * Speaks briefings. Implemented in the data layer by the playback service, which prepares the briefing itself:
 * started as a foreground service right away, it keeps playing when the user leaves the app (Android 17).
 */
interface VoicePlayer {
    val state: StateFlow<SpeechState>

    /** Starts [request] and replaces anything that was being prepared or spoken. */
    fun speak(request: BriefingRequest)

    /** Stops preparing or speaking. The playback service and its notification end. */
    fun stop()

    /**
     * The scheduled briefing screen is visible: the evening briefing that waits for it starts speaking, and when none
     * waits (the user opened the screen later), a new evening briefing starts.
     */
    fun onBriefingScreenShown()
}

/** Plays a ready playlist. The playback service implements it for the briefing it is preparing. */
fun interface PlaylistPlayer {
    /** False when playback could not start. */
    suspend fun play(playlist: VoicePlaylist, stream: PlaybackStream): Boolean
}
