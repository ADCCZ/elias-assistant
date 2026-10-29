package io.github.adccz.elias.data.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.data.schedule.EveningBriefingStarter
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.voice.BriefingPlaylist
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.BriefingRunner
import io.github.adccz.elias.domain.voice.PlaybackStream
import io.github.adccz.elias.domain.voice.PlaybackTrigger
import io.github.adccz.elias.domain.voice.PlaylistEntry
import io.github.adccz.elias.domain.voice.SpeakResult
import io.github.adccz.elias.domain.voice.SpeechState
import io.github.adccz.elias.domain.voice.VoicePlaylist
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Prepares and plays a spoken briefing in a foreground service of type `mediaPlayback`. Media3 shows the
 * notification (Znovu, Pause, Stop) and connects the lock screen, Bluetooth devices and the system media controls.
 *
 * A tap in the app and a scheduled alarm both start it with [speakIntent]. It goes into the foreground at once and
 * plays a silent loop titled "Připravuji…" while [BriefingRunner] loads the data, writes the briefing and turns it
 * into speech, then it swaps the loop for the briefing. So the service is in the foreground before it asks for audio
 * focus, as Android 17 requires, and a briefing keeps going when the user leaves the app while it is prepared.
 *
 * The scheduled evening briefing on a sleeping phone is prepared silently instead, with the screen dark and no audio
 * focus. When it is ready, the service shows the briefing screen, and the screen asks it to play ([screenShownIntent]):
 * the first word comes with the lit screen, and a visible app may always play on Android 17.
 *
 * The service ends when the briefing is over, after Stop, on an error or when another app takes the sound for good,
 * and the briefing's clips are deleted then.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var clock: Clock

    @Inject lateinit var files: VoiceFiles

    @Inject lateinit var runner: BriefingRunner

    @Inject lateinit var status: SpeechStatus

    @Inject
    @IoDispatcher
    lateinit var io: CoroutineDispatcher

    private var session: MediaSession? = null
    private lateinit var player: FocusAwarePlayer
    private val scope = MainScope()
    private var preparation: Job? = null
    private var screenFallback: Job? = null

    /**
     * What the service is doing: nothing, preparing (with the silent loop, or silently for the screen), waiting with a
     * ready briefing for its screen, or speaking.
     */
    private var phase: Phase = Phase.Idle

    /** The files of the playlist being played, so exactly this briefing's clips are deleted at the end. */
    private var playlistPaths: List<String> = emptyList()

    private sealed interface Phase {
        data object Idle : Phase

        data class Preparing(val kind: BriefingKind) : Phase

        /** The briefing is ready and waits for its screen, so the first word comes with the lit screen. */
        data class WaitingForScreen(val kind: BriefingKind, val items: List<MediaItem>, val stream: PlaybackStream) :
            Phase

        data class Speaking(val kind: BriefingKind) : Phase
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )
        // No notification without a briefing: after Stop or the end, nothing stays behind.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)
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
                    if (playbackState == Player.STATE_ENDED && phase is Phase.Speaking) stopSpeaking()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publish()

                // A clip that cannot be played ends the briefing instead of leaving a stuck notification.
                override fun onPlayerError(error: PlaybackException) = fail(SpeakResult.PlaybackFailed)
            },
        )
        player = FocusAwarePlayer(exoPlayer, this, clock, onFocusLostForGood = ::stopSpeaking)
        val builder = MediaSession.Builder(this, player)
            .setCallback(SessionCallback(player))
            .setMediaButtonPreferences(listOf(replayButton(), stopButton()))
        appLaunchIntent()?.let { builder.setSessionActivity(it) }
        session = builder.build().also {
            // No controller of the app connects any more, so the session is added here: Media3 then shows its
            // notification and keeps the service in the foreground while it plays.
            addSession(it)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val trusted = intent?.getStringExtra(EXTRA_TOKEN) == processToken
        return when (intent?.action) {
            ACTION_SPEAK -> {
                // Android wants startForeground() within seconds of startForegroundService(), even for a request
                // that is then ignored, so every branch calls it.
                val request = BriefingRequestCodec.decode(intent.getStringExtra(EXTRA_REQUEST))
                when {
                    trusted && request != null -> {
                        startPreparingInForeground(request.kind)
                        speak(request, waitForScreen = intent.getBooleanExtra(EXTRA_WAIT_FOR_SCREEN, false))
                    }

                    // A foreign or broken request must not stop the briefing being spoken: Media3 posts its
                    // notification again, which puts the service in the foreground.
                    phase != Phase.Idle -> session?.let { onUpdateNotification(it, true) }

                    else -> {
                        startPreparingInForeground(BriefingKind.MORNING)
                        stopSpeaking()
                    }
                }
                START_NOT_STICKY
            }

            ACTION_STOP -> {
                if (trusted) stopSpeaking()
                START_NOT_STICKY
            }

            ACTION_SCREEN_SHOWN -> {
                if (trusted) onScreenShown()
                START_NOT_STICKY
            }

            else -> result
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        if (status.state.value !is SpeechState.Failed) status.set(SpeechState.Idle)
        files.deleteBriefingsOf(playlistPaths)
        super.onDestroy()
    }

    /**
     * Starts preparing [request], replacing whatever was being prepared or spoken. With [waitForScreen] it prepares
     * silently and then waits for the briefing screen (see the class comment).
     */
    private fun speak(request: BriefingRequest, waitForScreen: Boolean = false) {
        preparation?.cancel()
        screenFallback?.cancel()
        phase = Phase.Preparing(request.kind)
        status.set(SpeechState.Preparing(request.kind))
        if (!waitForScreen) {
            player.setStream(request.trigger.stream)
            playSilentLoop(request.kind)
        }
        preparation = scope.launch {
            val result = runner.run(request) { playlist, stream ->
                if (waitForScreen) holdForScreen(playlist, stream) else playBriefing(playlist, stream)
            }
            if (result is SpeakResult.Problem) fail(result)
        }
    }

    /**
     * Keeps the ready briefing and lights up the screen with it. When the screen does not come (the phone is in use, or
     * full-screen notifications were switched off), the briefing plays without it after [SCREEN_WAIT_MILLIS].
     */
    private suspend fun holdForScreen(playlist: VoicePlaylist, stream: PlaybackStream): Boolean {
        val items = withContext(io) { playlist.toMediaItems() }
        if (phase !is Phase.Preparing) return false
        phase = Phase.WaitingForScreen(playlist.kind, items, stream)
        if (EveningBriefingStarter.showScreen(this)) {
            screenFallback = scope.launch {
                delay(SCREEN_WAIT_MILLIS)
                playWaitingBriefing()
            }
        } else {
            playWaitingBriefing()
        }
        return true
    }

    /** The briefing screen is visible: play the briefing waiting for it, or start one when none waits. */
    private fun onScreenShown() {
        when (phase) {
            is Phase.WaitingForScreen -> playWaitingBriefing()

            Phase.Idle -> {
                startPreparingInForeground(BriefingKind.EVENING)
                speak(BriefingRequest.FromToday(BriefingKind.EVENING, PlaybackTrigger.SCHEDULE))
            }

            // Already preparing or speaking: the screen only shows it.
            is Phase.Preparing, is Phase.Speaking -> Unit
        }
    }

    private fun playWaitingBriefing() {
        val waiting = phase as? Phase.WaitingForScreen ?: return
        screenFallback?.cancel()
        EveningBriefingStarter.hideScreenNotification(this)
        player.setStream(waiting.stream)
        playlistPaths = waiting.items.mapNotNull { it.localConfiguration?.uri?.path }
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.setMediaItems(waiting.items, true)
        player.prepare()
        player.play()
        phase = Phase.Speaking(waiting.kind)
        publish()
    }

    /**
     * The silent clip in a loop: Media3 sees playback, keeps the service in the foreground and shows "Připravuji…".
     * Asking for audio focus now also lowers music right away, which tells the user that Elias is about to speak.
     */
    private fun playSilentLoop(kind: BriefingKind) {
        val loop = MediaItem.Builder()
            .setMediaId(SILENT_LOOP_ID)
            .setUri(Uri.fromFile(files.silence(BriefingPlaylist.PAUSE_MILLIS)))
            .setMediaMetadata(metadata(kind, subtitle = getString(R.string.playback_preparing)))
            .build()
        playlistPaths = emptyList()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setMediaItems(listOf(loop), true)
        player.prepare()
        player.play()
    }

    /** Swaps the silent loop for the briefing. A pause the user made meanwhile stays a pause. */
    private suspend fun playBriefing(playlist: VoicePlaylist, stream: PlaybackStream): Boolean {
        // Building the items may write a silent clip, so it happens off the main thread.
        val items = withContext(io) { playlist.toMediaItems() }
        if (phase !is Phase.Preparing) return false
        player.setStream(stream)
        playlistPaths = items.mapNotNull { it.localConfiguration?.uri?.path }
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.setMediaItems(items, true)
        player.prepare()
        phase = Phase.Speaking(playlist.kind)
        publish()
        return true
    }

    /** Tells the screens what the player is doing. */
    private fun publish() {
        when (val current = phase) {
            is Phase.Speaking -> status.set(SpeechState.Speaking(current.kind, isPaused = !player.playWhenReady))
            is Phase.Preparing -> status.set(SpeechState.Preparing(current.kind))
            is Phase.WaitingForScreen -> status.set(SpeechState.Preparing(current.kind))
            Phase.Idle -> Unit
        }
    }

    private fun fail(problem: SpeakResult.Problem) {
        val kind = when (val current = phase) {
            is Phase.Preparing -> current.kind
            is Phase.WaitingForScreen -> current.kind
            is Phase.Speaking -> current.kind
            Phase.Idle -> null
        }
        stopSpeaking()
        kind?.let { status.set(SpeechState.Failed(it, problem)) }
    }

    /** Ends the briefing. An empty playlist removes the notification, then the service stops itself. */
    private fun stopSpeaking() {
        preparation?.cancel()
        preparation = null
        screenFallback?.cancel()
        EveningBriefingStarter.hideScreenNotification(this)
        phase = Phase.Idle
        status.set(SpeechState.Idle)
        session?.player?.run {
            stop()
            clearMediaItems()
        }
        pauseAllPlayersAndStopSelf()
    }

    /**
     * A plain "Připravuji…" notification for the first moment, until Media3 replaces it with the media notification
     * under the same ID. It uses Media3's channel, created here in case Media3 has not created it yet.
     */
    private fun startPreparingInForeground(kind: BriefingKind) {
        val channelId = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                channelId,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(titleOf(kind)))
            .setContentText(getString(R.string.playback_preparing))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setSilent(true)
            .build()
        ServiceCompat.startForeground(
            this,
            DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun VoicePlaylist.toMediaItems(): List<MediaItem> {
        // The same title on every item, so the notification does not change between sections and pauses.
        val metadata = metadata(kind, subtitle = getString(R.string.app_name))
        return entries.mapIndexed { index, entry ->
            val file = when (entry) {
                is PlaylistEntry.Speech -> File(entry.clip.path)
                is PlaylistEntry.Pause -> files.silence(entry.millis)
            }
            MediaItem.Builder()
                .setMediaId(PlaylistMediaIds.of(entry, index))
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(metadata)
                .build()
        }
    }

    private fun metadata(kind: BriefingKind, subtitle: String) = MediaMetadata.Builder()
        .setTitle(getString(titleOf(kind)))
        .setArtist(subtitle)
        .build()

    private fun titleOf(kind: BriefingKind) = when (kind) {
        BriefingKind.MORNING -> R.string.playback_title_morning
        BriefingKind.EVENING -> R.string.playback_title_evening
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
         * The system (a trusted controller) gets Elias's commands. Seeking and skipping are taken away: one section
         * is one item, so a seek bar or "next" inside a briefing would only confuse. Other apps keep Media3's
         * default read-only access.
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
                            // Briefings come only from the app and alarms, never from another controller.
                            Player.COMMAND_SET_MEDIA_ITEM,
                            Player.COMMAND_CHANGE_MEDIA_ITEMS,
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
    }

    companion object {
        private const val ACTION_SPEAK = "io.github.adccz.elias.SPEAK"
        private const val ACTION_STOP = "io.github.adccz.elias.STOP_SPEAKING"
        private const val ACTION_SCREEN_SHOWN = "io.github.adccz.elias.SCREEN_SHOWN"
        private const val EXTRA_WAIT_FOR_SCREEN = "io.github.adccz.elias.WAIT_FOR_SCREEN"
        private const val EXTRA_REQUEST = "io.github.adccz.elias.REQUEST"
        private const val EXTRA_TOKEN = "io.github.adccz.elias.TOKEN"
        private const val SILENT_LOOP_ID = "preparing"

        /** How long a ready evening briefing waits for its screen before it plays without it. */
        private const val SCREEN_WAIT_MILLIS = 15_000L

        /**
         * A secret of this app process. The service is exported (system media controls connect to it), so another
         * app could send it an intent. Only intents that carry this token, made inside the app, are obeyed.
         */
        private val processToken = UUID.randomUUID().toString()

        /**
         * Starts preparing and speaking [request]. Use with startForegroundService(). With [waitForScreen] the briefing
         * is prepared silently and then shown on the briefing screen, which starts the first word.
         */
        fun speakIntent(context: Context, request: BriefingRequest, waitForScreen: Boolean = false): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_SPEAK)
                .putExtra(EXTRA_REQUEST, BriefingRequestCodec.encode(request))
                .putExtra(EXTRA_WAIT_FOR_SCREEN, waitForScreen)
                .putExtra(EXTRA_TOKEN, processToken)

        /** The briefing screen is visible. Use with startService() from the visible screen. */
        fun screenShownIntent(context: Context): Intent = Intent(context, PlaybackService::class.java)
            .setAction(ACTION_SCREEN_SHOWN)
            .putExtra(EXTRA_TOKEN, processToken)

        /** Stops preparing or speaking. Use with startService() while the service runs. */
        fun stopIntent(context: Context): Intent = Intent(context, PlaybackService::class.java)
            .setAction(ACTION_STOP)
            .putExtra(EXTRA_TOKEN, processToken)
    }
}
