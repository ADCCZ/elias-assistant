package io.github.adccz.elias.data.voice

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.R
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.voice.PlaybackState
import io.github.adccz.elias.domain.voice.PlaybackStream
import io.github.adccz.elias.domain.voice.PlaylistEntry
import io.github.adccz.elias.domain.voice.VoicePlayer
import io.github.adccz.elias.domain.voice.VoicePlaylist
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/**
 * The app's side of playback: a Media3 [MediaController] that talks to [PlaybackService].
 *
 * The controller is connected only while a briefing plays. The service can stop only when no controller is
 * bound to it, so the controller is released as soon as the briefing ends.
 */
@Singleton
class MediaControllerVoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val files: VoiceFiles,
) : VoicePlayer {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    /** The briefing being played. Null while idle or while a new playlist is being set up. */
    private var playing: BriefingKind? = null

    /** The service reported playback for the current briefing. Until then an idle player is just starting. */
    private var hasStarted = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val kind = playing ?: return
            when (player.playbackState) {
                Player.STATE_BUFFERING, Player.STATE_READY -> hasStarted = true
            }
            val isOver = player.playbackState == Player.STATE_ENDED ||
                (hasStarted && (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0))
            if (isOver) {
                finish()
            } else {
                _state.value = PlaybackState.Speaking(kind, isPaused = !player.playWhenReady)
            }
        }
    }

    override suspend fun play(playlist: VoicePlaylist, stream: PlaybackStream): Boolean {
        if (playlist.entries.isEmpty()) return false
        // Building the items may write the silent clip, so it happens off the main thread.
        val items = withContext(io) { playlist.toMediaItems(stream) }
        return withContext(Dispatchers.Main) {
            val controller = connect() ?: return@withContext false
            playing = null
            controller.setMediaItems(items)
            controller.prepare()
            controller.play()
            playing = playlist.kind
            hasStarted = false
            _state.value = PlaybackState.Speaking(playlist.kind, isPaused = false)
            true
        }
    }

    override fun stop() {
        mainHandler.post { controller?.sendCustomCommand(PlaybackCommands.stop, Bundle.EMPTY) }
    }

    /** Connects to the playback service (Android starts it when needed). Null when that fails. */
    private suspend fun connect(): MediaController? {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        return try {
            MediaController.Builder(context, token)
                .setListener(
                    object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            this@MediaControllerVoicePlayer.controller = null
                            playing = null
                            _state.value = PlaybackState.Idle
                        }
                    },
                )
                .buildAsync()
                .await()
                .also {
                    it.addListener(listener)
                    controller = it
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun finish() {
        playing = null
        _state.value = PlaybackState.Idle
        // Released after the current callback. A briefing started in the meantime keeps the controller.
        mainHandler.post {
            if (playing == null) {
                controller?.release()
                controller = null
            }
        }
    }

    private fun VoicePlaylist.toMediaItems(stream: PlaybackStream): List<MediaItem> {
        val title = when (kind) {
            BriefingKind.MORNING -> R.string.playback_title_morning
            BriefingKind.EVENING -> R.string.playback_title_evening
        }
        // The same title on every item, so the notification does not change between sections and pauses.
        val metadata = MediaMetadata.Builder()
            .setTitle(context.getString(title))
            .setArtist(context.getString(R.string.app_name))
            .build()
        val request = PlaybackCommands.requestMetadata(stream)
        return entries.mapIndexed { index, entry ->
            val file = when (entry) {
                is PlaylistEntry.Speech -> File(entry.clip.path)
                is PlaylistEntry.Pause -> files.silence(entry.millis)
            }
            MediaItem.Builder()
                .setMediaId(PlaylistMediaIds.of(entry, index))
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(metadata)
                .setRequestMetadata(request)
                .build()
        }
    }
}
