package io.github.adccz.elias.data.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.domain.schedule.AlarmScheduler
import io.github.adccz.elias.domain.schedule.ScheduledRun
import io.github.adccz.elias.domain.schedule.ScheduledTask
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Elias's alarms in Android's AlarmManager. They fire on time even in Doze (the phone's deep sleep at night).
 *
 * - The evening briefing is an alarm clock (`setAlarmClock`): the most reliable kind, shown as the next alarm on the
 *   lock screen, and Android lets it start the playback service from the background.
 * - The prefetch and the sabbath reminder are exact alarms without the alarm icon: nothing the user needs to see.
 *
 * Exact alarms need USE_EXACT_ALARM (granted at install, see D29). Without it a run comes a little late instead.
 */
@Singleton
class AndroidAlarmScheduler @Inject constructor(@ApplicationContext private val context: Context) : AlarmScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun set(run: ScheduledRun) {
        val operation = ScheduledRunReceiver.pendingIntent(context, run)
        val at = run.at.toInstant().toEpochMilli()
        try {
            when (run.task) {
                ScheduledTask.EVENING_BRIEFING ->
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, appLaunchIntent()), operation)

                ScheduledTask.PREFETCH, ScheduledTask.SABBATH_REMINDER ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    override fun cancel(task: ScheduledTask) {
        ScheduledRunReceiver.existingPendingIntent(context, task)?.let { alarmManager.cancel(it) }
    }

    /** What opens when the user taps the next alarm on the lock screen: the app. */
    private fun appLaunchIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
}
