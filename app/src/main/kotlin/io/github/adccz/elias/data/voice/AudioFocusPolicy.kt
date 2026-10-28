package io.github.adccz.elias.data.voice

import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Audio focus changes as Android reports them, without Android types, so the policy runs in plain unit tests. */
enum class FocusChange {
    GAINED,

    /** Someone needs the sound for a while: a phone call, a navigation prompt, another app speaking. */
    LOST_TRANSIENTLY,

    /** Another app took the sound for good, for example the user started music. */
    LOST,
}

/** What the player has to do after an audio focus change. */
enum class FocusAction { NONE, PAUSE, RESUME, ABANDON, STOP }

/**
 * Decides how Elias shares the sound with other apps ("audio focus").
 *
 * ExoPlayer can handle focus itself only for media and games, and then it asks for permanent focus: a music app
 * would stop and not come back. Elias asks for transient focus instead (the adapter does the Android calls), so
 * music only gets quieter while Elias speaks and comes back afterwards. It works the same on the alarm stream.
 *
 * - A phone call or another short interruption pauses Elias, and it continues afterwards, but only within
 *   [resumeWindow]: after that Android 17 no longer lets a background service play without a user action.
 * - When another app takes the sound for good, Elias stops.
 * - When the user pauses or the briefing ends, focus is given back, so other apps can play normally again.
 */
class AudioFocusPolicy(private val clock: Clock, private val resumeWindow: Duration = DEFAULT_RESUME_WINDOW) {
    /** True while Android knows Elias as an app that has the sound, or gets it back after an interruption. */
    var holdsFocus = false
        private set

    private var interruptedAt: Instant? = null
    private var expectingOwnPause = false

    /** The player is about to play. Returns true when focus has to be requested first. */
    fun onPlayRequested(): Boolean = !holdsFocus || interruptedAt != null

    /** The answer to a focus request. The player may play only when it was granted. */
    fun onFocusRequestResult(granted: Boolean) {
        // Refused during a call: Elias stays registered and continues when the call ends.
        if (granted) {
            holdsFocus = true
            interruptedAt = null
        }
    }

    /** [wantsToPlay] is the player's playWhenReady: true while playing or about to play. */
    fun onFocusChange(change: FocusChange, wantsToPlay: Boolean): FocusAction = when (change) {
        FocusChange.LOST_TRANSIENTLY -> if (wantsToPlay) interrupt() else FocusAction.NONE

        FocusChange.GAINED -> regained()

        FocusChange.LOST -> {
            holdsFocus = false
            interruptedAt = null
            FocusAction.STOP
        }
    }

    /** The player paused, for any reason. Returns true when focus should be given back. */
    fun onPaused(): Boolean {
        if (expectingOwnPause) {
            expectingOwnPause = false
            return false
        }
        interruptedAt = null
        return release()
    }

    /** Playback ended or stopped, or the player was released. Returns true when focus should be given back. */
    fun onStopped(): Boolean {
        interruptedAt = null
        expectingOwnPause = false
        return release()
    }

    private fun interrupt(): FocusAction {
        interruptedAt = clock.instant()
        expectingOwnPause = true
        return FocusAction.PAUSE
    }

    private fun regained(): FocusAction {
        val since = interruptedAt ?: return FocusAction.NONE
        interruptedAt = null
        if (Duration.between(since, clock.instant()) <= resumeWindow) return FocusAction.RESUME
        // Too late to continue on its own. Elias stays paused and lets other apps play.
        holdsFocus = false
        return FocusAction.ABANDON
    }

    private fun release(): Boolean = holdsFocus.also { holdsFocus = false }

    companion object {
        /** Media3 keeps the playback service in the foreground for 10 minutes after a pause, Android 17 asks the same. */
        val DEFAULT_RESUME_WINDOW: Duration = Duration.ofMinutes(10)
    }
}
