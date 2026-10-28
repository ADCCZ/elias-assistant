package io.github.adccz.elias.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.adccz.elias.R

/**
 * Elias's own notification channels (the playback channel belongs to Media3). Users can mute each one separately
 * in the phone's settings. Creating a channel that exists already changes nothing, so this runs at every start.
 */
object NotificationChannels {
    /** The notification of the prefetch, shown only on Android 11 and older while it runs. */
    const val PREFETCH = "prefetch"

    /** The Friday reminder an hour before the sabbath. */
    const val SABBATH = "sabbath"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                PREFETCH,
                context.getString(R.string.channel_prefetch),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                SABBATH,
                context.getString(R.string.channel_sabbath),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
