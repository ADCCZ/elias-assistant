package io.github.adccz.elias.data.schedule

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.adccz.elias.data.notifications.SabbathReminderNotifier
import io.github.adccz.elias.domain.schedule.ScheduleCalculator
import io.github.adccz.elias.domain.schedule.ScheduleKeeper
import io.github.adccz.elias.domain.schedule.ScheduledRun
import io.github.adccz.elias.domain.schedule.ScheduledTask
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * Runs a scheduled task when its alarm fires, then plans the next alarms. Saturday's evening briefing needs no flag:
 * the briefing itself sees that the sabbath has just ended. Not exported: only the app's own
 * alarms reach it.
 */
class ScheduledRunReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val task = ScheduledTask.entries.firstOrNull { actionOf(it) == intent.action } ?: return
        val dependencies = ScheduleEntryPoint.from(context)
        val plannedAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_PLANNED_AT, 0))
        if (ScheduleKeeper.isStillDue(task, plannedAt, dependencies.clock().instant())) {
            when (task) {
                ScheduledTask.EVENING_BRIEFING -> EveningBriefingStarter.start(context)

                ScheduledTask.PREFETCH -> PrefetchWorker.enqueue(context)

                ScheduledTask.SABBATH_REMINDER ->
                    SabbathReminderNotifier.show(
                        context,
                        plannedAt.plus(ScheduleCalculator.REMINDER_LEAD).atZone(dependencies.zone()),
                    )
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

    companion object {
        private const val EXTRA_PLANNED_AT = "io.github.adccz.elias.PLANNED_AT"

        /** One action per task: AlarmManager keeps one alarm per distinct intent, so each task has its own. */
        private fun actionOf(task: ScheduledTask) = "io.github.adccz.elias.RUN_${task.name}"

        private fun intent(context: Context, task: ScheduledTask) =
            Intent(context, ScheduledRunReceiver::class.java).setAction(actionOf(task))

        fun pendingIntent(context: Context, run: ScheduledRun): PendingIntent = PendingIntent.getBroadcast(
            context,
            run.task.ordinal,
            intent(context, run.task)
                .putExtra(EXTRA_PLANNED_AT, run.at.toInstant().toEpochMilli()),
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
