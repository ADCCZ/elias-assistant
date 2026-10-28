package io.github.adccz.elias.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import io.github.adccz.elias.domain.voice.PlaybackStream

/**
 * Requests and gives back audio focus through Android's AudioManager. Thin on purpose: what to do with a focus
 * change is decided by [AudioFocusPolicy]. Must be used on the main thread, where changes are reported too.
 */
class PlatformAudioFocus(context: Context, private val onChange: (FocusChange) -> Unit) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onChange(FocusChange.GAINED)

            AudioManager.AUDIOFOCUS_LOSS -> onChange(FocusChange.LOST)

            // With "pause when ducked" Android does not lower Elias itself, it asks Elias to pause.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> onChange(FocusChange.LOST_TRANSIENTLY)
        }
    }
    private var lastRequest: AudioFocusRequest? = null

    /** The stream the next request is made for. */
    var stream: PlaybackStream = PlaybackStream.MEDIA

    /**
     * Asks for transient focus that lets other apps continue quieter ("may duck"): music is lowered while Elias
     * speaks and comes back afterwards. Elias itself pauses instead of being lowered, because quiet speech is
     * hard to follow. Returns true when focus was granted.
     */
    fun request(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(stream.platformAttributes())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(listener, handler)
            .build()
        lastRequest = request
        return try {
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: IllegalStateException) {
            // Android 17 refuses background audio this way when hardening is set to "throw" (a test mode).
            // A refusal must never crash the playback service: it is treated like any other refused request.
            false
        }
    }

    /** Gives the focus back, so other apps play at full volume again. Safe to call when nothing is held. */
    fun abandon() {
        lastRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}

/** Android's audio attributes for [PlaybackStream]: the usage picks the volume, the content type says "speech". */
fun PlaybackStream.platformAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(
        when (this) {
            PlaybackStream.MEDIA -> AudioAttributes.USAGE_MEDIA
            PlaybackStream.ALARM -> AudioAttributes.USAGE_ALARM
        },
    )
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()
