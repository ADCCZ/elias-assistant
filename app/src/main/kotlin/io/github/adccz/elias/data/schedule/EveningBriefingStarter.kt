package io.github.adccz.elias.data.schedule

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.adccz.elias.R
import io.github.adccz.elias.core.android.canPostNotifications
import io.github.adccz.elias.data.notifications.NotificationChannels
import io.github.adccz.elias.data.voice.PlaybackService
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.schedule.EveningStart
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackTrigger

/**
 * Starts the scheduled evening briefing (see [EveningStart] for which way):
 *
 * - **Screen:** the playback service prepares the briefing silently with the screen dark. When it is ready, it shows
 *   the briefing screen with [showScreen], and the screen starts the first word. The screen is lit while Elias speaks,
 *   not while the briefing is written and turned into speech (the user's wish).
 * - **Sound:** the playback service prepares and speaks without a screen.
 */
object EveningBriefingStarter {
    const val NOTIFICATION_ID = 4001

    /** Opens the briefing screen (ScheduledBriefingActivity). An action keeps the data layer free of UI classes. */
    const val ACTION_SHOW_SCREEN = "io.github.adccz.elias.SHOW_SCHEDULED_BRIEFING"

    private val request = BriefingRequest.FromToday(BriefingKind.EVENING, PlaybackTrigger.SCHEDULE)

    /** An alarm clock alarm may start a foreground service from the background. */
    fun start(context: Context) {
        val waitForScreen = EveningStart.choose(canShowFullScreen(context), phoneInUse(context)) == EveningStart.SCREEN
        ContextCompat.startForegroundService(context, PlaybackService.speakIntent(context, request, waitForScreen))
    }

    /** Whether Android lets Elias light up the screen with a full-screen notification, as alarm clocks do. */
    fun canShowFullScreen(context: Context): Boolean = context.canPostNotifications() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            )

    private fun phoneInUse(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    /**
     * A full-screen notification: on a sleeping phone Android opens the briefing screen at once and switches the screen
     * on. Returns false when it could not be shown (the permission was taken away just now).
     */
    fun showScreen(context: Context): Boolean {
        val screen = PendingIntent.getActivity(
            context,
            0,
            Intent(ACTION_SHOW_SCREEN)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.EVENING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.playback_title_evening))
            .setContentText(context.getString(R.string.scheduled_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setAutoCancel(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MILLIS)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Removes the full-screen notification once the briefing plays. */
    fun hideScreenNotification(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    /** A notification nobody opened is gone after ten minutes: the evening has moved on. */
    private const val NOTIFICATION_TIMEOUT_MILLIS = 10 * 60 * 1000L
}
