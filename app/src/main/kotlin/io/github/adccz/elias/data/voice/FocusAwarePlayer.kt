package io.github.adccz.elias.data.voice

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.adccz.elias.domain.voice.PlaybackStream
import java.time.Clock

/**
 * ExoPlayer with Elias's own audio focus handling (see [AudioFocusPolicy] for why ExoPlayer's is not used).
 *
 * Every play request, from the app, the notification or a headset button, goes through [play] or
 * [setPlayWhenReady], so focus is requested before any sound is made. Pauses and the end of playback
 * are seen by a listener, whatever caused them.
 */
@OptIn(UnstableApi::class)
class FocusAwarePlayer(
    private val exoPlayer: ExoPlayer,
    context: Context,
    clock: Clock,
    private val onFocusLostForGood: () -> Unit,
) : ForwardingPlayer(exoPlayer) {
    private val policy = AudioFocusPolicy(clock)
    private val focus = PlatformAudioFocus(context, ::onFocusChange)

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && policy.onPaused()) focus.abandon()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stopped = playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE
                    if (stopped && policy.onStopped()) focus.abandon()
                }
            },
        )
    }

    /** Sets the stream for the next briefing: the volume it uses and the attributes of the focus request. */
    fun setStream(stream: PlaybackStream) {
        exoPlayer.setAudioAttributes(stream.media3Attributes(), false)
        focus.stream = stream
    }

    override fun play() {
        if (mayPlay()) super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (!playWhenReady || mayPlay()) super.setPlayWhenReady(playWhenReady)
    }

    override fun release() {
        if (policy.onStopped()) focus.abandon()
        super.release()
    }

    private fun mayPlay(): Boolean {
        if (!policy.onPlayRequested()) return true
        val granted = focus.request()
        policy.onFocusRequestResult(granted)
        return granted
    }

    private fun onFocusChange(change: FocusChange) {
        when (policy.onFocusChange(change, wantsToPlay = exoPlayer.playWhenReady)) {
            FocusAction.NONE -> Unit
            FocusAction.PAUSE -> exoPlayer.pause()
            FocusAction.RESUME -> exoPlayer.play()
            FocusAction.ABANDON -> focus.abandon()
            FocusAction.STOP -> onFocusLostForGood()
        }
    }
}
