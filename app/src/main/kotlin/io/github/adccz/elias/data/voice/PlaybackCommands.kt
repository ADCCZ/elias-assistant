package io.github.adccz.elias.data.voice

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.session.SessionCommand
import io.github.adccz.elias.domain.voice.PlaybackStream

/** The playback service's own commands, sent by its notification buttons. */
object PlaybackCommands {
    const val ACTION_REPLAY = "io.github.adccz.elias.REPLAY"
    const val ACTION_STOP = "io.github.adccz.elias.STOP"

    /** "Znovu": the section being spoken starts again. */
    val replay = SessionCommand(ACTION_REPLAY, Bundle.EMPTY)

    /** "Stop": the briefing ends and the service with it. */
    val stop = SessionCommand(ACTION_STOP, Bundle.EMPTY)
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
