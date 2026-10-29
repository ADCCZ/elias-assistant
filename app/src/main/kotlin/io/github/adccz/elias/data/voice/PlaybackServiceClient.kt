package io.github.adccz.elias.data.voice

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.SpeechState
import io.github.adccz.elias.domain.voice.VoicePlayer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's side of speaking: it hands each request to [PlaybackService] and shows the service's state.
 *
 * The service is started as a foreground service right at the tap, while the app is visible. Android 17 lets only
 * such a service keep playing when the user leaves the app during "Připravuji…" (the M3 phone test found this).
 */
@Singleton
class PlaybackServiceClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val status: SpeechStatus,
) : VoicePlayer {
    override val state: StateFlow<SpeechState> = status.state

    override fun speak(request: BriefingRequest) {
        // Shown at once: the service needs a moment to start.
        status.set(SpeechState.Preparing(request.kind))
        try {
            ContextCompat.startForegroundService(context, PlaybackService.speakIntent(context, request))
        } catch (e: IllegalStateException) {
            // Android refused to start the service (the app was not in the foreground after all).
            status.set(SpeechState.Failed(request.kind, SpeakResult.PlaybackFailed))
        }
    }

    override fun onBriefingScreenShown() {
        // The screen is visible, so the app may start the service; it decides what to play.
        try {
            context.startService(PlaybackService.screenShownIntent(context))
        } catch (e: IllegalStateException) {
            status.set(SpeechState.Failed(BriefingKind.EVENING, SpeakResult.PlaybackFailed))
        }
    }

    override fun stop() {
        when (status.state.value) {
            is SpeechState.Preparing, is SpeechState.Speaking -> try {
                context.startService(PlaybackService.stopIntent(context))
            } catch (e: IllegalStateException) {
                // The service is not running any more, so there is nothing to stop.
                status.set(SpeechState.Idle)
            }

            // Nothing is running: stopping only clears a failure from the screen.
            SpeechState.Idle, is SpeechState.Failed -> status.set(SpeechState.Idle)
        }
    }
}
