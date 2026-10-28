package io.github.adccz.elias.data.voice

import android.app.PendingIntent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.voice.PlaybackStream
import java.time.Clock
import javax.inject.Inject

/**
 * Plays a spoken briefing in a foreground service of type `mediaPlayback`. Android 17 lets sound go on with the
 * screen off only like this. Media3 shows the notification (Znovu, Pause, Stop) and connects the lock screen,
 * Bluetooth devices and the system media controls.
 *
 * The service starts when the app plays a briefing. It ends when the briefing is over, after Stop, or when another
 * app takes the sound for good, and the spoken clips are deleted then.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var clock: Clock

    @Inject lateinit var files: VoiceFiles

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )
        val exoPlayer = ExoPlayer.Builder(this)
            // false: ExoPlayer leaves audio focus alone, FocusAwarePlayer handles it.
            .setAudioAttributes(PlaybackStream.MEDIA.media3Attributes(), false)
            // Keeps the processor awake while Elias speaks with the screen off.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            // Pauses when headphones are unplugged instead of going on through the speaker.
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stopSpeaking()
                }
            },
        )
        val player = FocusAwarePlayer(exoPlayer, this, clock, onFocusLostForGood = ::stopSpeaking)
        val builder = MediaSession.Builder(this, player)
            .setCallback(SessionCallback(player))
            .setMediaButtonPreferences(listOf(replayButton(), stopButton()))
        appLaunchIntent()?.let { builder.setSessionActivity(it) }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        files.deleteClips()
        super.onDestroy()
    }

    /** Ends the briefing. An empty playlist removes the notification, then the service stops itself. */
    private fun stopSpeaking() {
        session?.player?.run {
            stop()
            clearMediaItems()
        }
        pauseAllPlayersAndStopSelf()
    }

    private fun replayCurrentSection(player: Player) {
        val mediaIds = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        player.seekTo(PlaylistMediaIds.replayIndex(mediaIds, player.currentMediaItemIndex), 0)
        player.play()
    }

    private fun replayButton() = CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
        .setDisplayName(getString(R.string.playback_replay))
        .setSessionCommand(PlaybackCommands.replay)
        .setSlots(CommandButton.SLOT_BACK)
        .build()

    private fun stopButton() = CommandButton.Builder(CommandButton.ICON_STOP)
        .setDisplayName(getString(R.string.playback_stop))
        .setSessionCommand(PlaybackCommands.stop)
        .setSlots(CommandButton.SLOT_FORWARD)
        .build()

    /** Tapping the notification opens the app. */
    private fun appLaunchIntent(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private inner class SessionCallback(private val player: FocusAwarePlayer) : MediaSession.Callback {
        /**
         * The app, the notification and the system (trusted controllers) get Elias's commands. Seeking and skipping
         * are taken away: one section is one item, so a seek bar or "next" inside a briefing would only confuse.
         * Other apps keep Media3's default read-only access.
         */
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            if (controller.isTrusted) {
                result.setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(PlaybackCommands.replay)
                        .add(PlaybackCommands.stop)
                        .build(),
                )
                result.setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .removeAll(
                            Player.COMMAND_SEEK_TO_PREVIOUS,
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                            Player.COMMAND_SEEK_TO_NEXT,
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        )
                        .build(),
                )
            }
            return Futures.immediateFuture(result.build())
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.ACTION_REPLAY -> replayCurrentSection(player)
                PlaybackCommands.ACTION_STOP -> stopSpeaking()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** The stream travels with the playlist, so it is set before the first sound. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            player.setStream(PlaybackCommands.streamOf(mediaItems))
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
    }
}
