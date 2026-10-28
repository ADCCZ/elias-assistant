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
 * Starts the scheduled evening briefing: with the briefing screen over the lock screen, or as sound only
 * (see [EveningStart] for when each one is used).
 */
object EveningBriefingStarter {
    const val NOTIFICATION_ID = 4001

    /** Opens the briefing screen (ScheduledBriefingActivity). An action keeps the data layer free of UI classes. */
    const val ACTION_SHOW_SCREEN = "io.github.adccz.elias.SHOW_SCHEDULED_BRIEFING"

    fun start(context: Context) {
        when (EveningStart.choose(canShowFullScreen(context), phoneInUse(context))) {
            EveningStart.SCREEN -> showScreen(context)
            EveningStart.SOUND -> startSound(context)
        }
    }

    /** An alarm clock alarm may start a foreground service from the background. */
    private fun startSound(context: Context) = ContextCompat.startForegroundService(
        context,
        PlaybackService.speakIntent(context, BriefingRequest.FromToday(BriefingKind.EVENING, PlaybackTrigger.SCHEDULE)),
    )

    /** Whether Android lets Elias light up the screen with a full-screen notification, as alarm clocks do. */
    fun canShowFullScreen(context: Context): Boolean = context.canPostNotifications() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            )

    private fun phoneInUse(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    /** A full-screen notification: on a sleeping phone Android opens the briefing screen at once. */
    private fun showScreen(context: Context) {
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
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The permission was taken away just now: the briefing still plays, only without the screen.
            startSound(context)
        }
    }

    /** A notification nobody opened is gone after ten minutes: the evening has moved on. */
    private const val NOTIFICATION_TIMEOUT_MILLIS = 10 * 60 * 1000L
}
