package io.github.adccz.elias.data.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * Plans the alarms again after the phone restarts, the clock or the time zone changes, or the app is updated:
 * Android forgets alarms on a restart, and after a time change they would fire at the wrong moment.
 *
 * It never plays anything itself. Android 15 and newer do not let a media service start after a restart, and a
 * briefing nobody asked for right after switching the phone on would not be quiet anyway.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val dependencies = ScheduleEntryPoint.from(context)
        val pending = goAsync()
        dependencies.applicationScope().launch {
            try {
                dependencies.scheduleKeeper().reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
