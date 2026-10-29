package io.github.adccz.elias.data.notifications

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.adccz.elias.R
import io.github.adccz.elias.core.android.canPostNotifications
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The Friday reminder an hour before the sabbath begins (spec): a quiet notification with what to do before sunset.
 * It is only shown, never spoken (the user's choice in the M4 plan).
 */
object SabbathReminderNotifier {
    private const val NOTIFICATION_ID = 3001
    private val time = DateTimeFormatter.ofPattern("H:mm")

    /** Shows the reminder for the sabbath that begins at [sunset]. Does nothing without the notification permission. */
    fun show(context: Context, sunset: ZonedDateTime) {
        if (!context.canPostNotifications()) return
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = context.getString(R.string.sabbath_reminder_text, sunset.format(time))
        val notification = NotificationCompat.Builder(context, NotificationChannels.SABBATH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.sabbath_reminder_title))
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The permission was taken away just now. Nothing else to do: the reminder is a courtesy.
        }
    }
}
