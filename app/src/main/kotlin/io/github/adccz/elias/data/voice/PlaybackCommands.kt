package io.github.adccz.elias.data.voice

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.SessionCommand
import io.github.adccz.elias.domain.voice.PlaybackStream

/** What the app and the playback service agree on: their own commands and how the stream travels with a playlist. */
object PlaybackCommands {
    const val ACTION_REPLAY = "io.github.adccz.elias.REPLAY"
    const val ACTION_STOP = "io.github.adccz.elias.STOP"
    private const val EXTRA_STREAM = "io.github.adccz.elias.STREAM"

    /** "Znovu": the section being spoken starts again. */
    val replay = SessionCommand(ACTION_REPLAY, Bundle.EMPTY)

    /** "Stop": the briefing ends and the service with it. */
    val stop = SessionCommand(ACTION_STOP, Bundle.EMPTY)

    /** Sent with each media item, so the service sets the right stream before it plays. */
    fun requestMetadata(stream: PlaybackStream): MediaItem.RequestMetadata = MediaItem.RequestMetadata.Builder()
        .setExtras(Bundle().apply { putString(EXTRA_STREAM, stream.name) })
        .build()

    /** The stream the app asked for. Media when nothing was sent. */
    fun streamOf(items: List<MediaItem>): PlaybackStream {
        val name = items.firstOrNull()?.requestMetadata?.extras?.getString(EXTRA_STREAM)
        return PlaybackStream.entries.firstOrNull { it.name == name } ?: PlaybackStream.MEDIA
    }
}

/** Media3's audio attributes for [PlaybackStream]: the usage picks the volume, the content type says "speech". */
fun PlaybackStream.media3Attributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(
        when (this) {
            PlaybackStream.MEDIA -> C.USAGE_MEDIA
            PlaybackStream.ALARM -> C.USAGE_ALARM
        },
    )
    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
    .build()
