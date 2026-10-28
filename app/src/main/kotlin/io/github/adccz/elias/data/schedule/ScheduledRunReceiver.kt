package io.github.adccz.elias.data.schedule

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.adccz.elias.data.voice.PlaybackService
import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.schedule.ScheduleKeeper
import io.github.adccz.elias.domain.schedule.ScheduledRun
import io.github.adccz.elias.domain.schedule.ScheduledTask
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackTrigger
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * Runs a scheduled task when its alarm fires, then plans the next alarms. Not exported: only the app's own
 * alarms reach it.
 */
class ScheduledRunReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val task = ScheduledTask.entries.firstOrNull { actionOf(it) == intent.action } ?: return
        val dependencies = ScheduleEntryPoint.from(context)
        val plannedAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_PLANNED_AT, 0))
        if (ScheduleKeeper.isStillDue(task, plannedAt, dependencies.clock().instant())) {
            when (task) {
                ScheduledTask.EVENING_BRIEFING -> startEveningBriefing(context)
                ScheduledTask.PREFETCH -> PrefetchWorker.enqueue(context)
                ScheduledTask.SABBATH_REMINDER -> Unit
            }
        }
        // The next alarms. goAsync() keeps the receiver alive until they are set.
        val pending = goAsync()
        dependencies.applicationScope().launch {
            try {
                dependencies.scheduleKeeper().reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * An alarm clock alarm may start a foreground service from the background. The briefing plays on the alarm
     * stream, which Android 17 allows without "while in use" access because the app has the exact alarm permission.
     */
    private fun startEveningBriefing(context: Context) {
        val request = BriefingRequest.FromToday(BriefingKind.EVENING, PlaybackTrigger.SCHEDULE)
        ContextCompat.startForegroundService(context, PlaybackService.speakIntent(context, request))
    }

    companion object {
        private const val EXTRA_PLANNED_AT = "io.github.adccz.elias.PLANNED_AT"
        private const val EXTRA_AFTER_SABBATH = "io.github.adccz.elias.AFTER_SABBATH"

        /** One action per task: AlarmManager keeps one alarm per distinct intent, so each task has its own. */
        private fun actionOf(task: ScheduledTask) = "io.github.adccz.elias.RUN_${task.name}"

        private fun intent(context: Context, task: ScheduledTask) =
            Intent(context, ScheduledRunReceiver::class.java).setAction(actionOf(task))

        fun pendingIntent(context: Context, run: ScheduledRun): PendingIntent = PendingIntent.getBroadcast(
            context,
            run.task.ordinal,
            intent(context, run.task)
                .putExtra(EXTRA_PLANNED_AT, run.at.toInstant().toEpochMilli())
                .putExtra(EXTRA_AFTER_SABBATH, run.afterSabbath),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** The task's alarm intent if one is set, otherwise null. */
        fun existingPendingIntent(context: Context, task: ScheduledTask): PendingIntent? = PendingIntent.getBroadcast(
            context,
            task.ordinal,
            intent(context, task),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
    }
}
