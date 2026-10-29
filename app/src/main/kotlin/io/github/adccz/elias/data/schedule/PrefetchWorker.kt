package io.github.adccz.elias.data.schedule

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.adccz.elias.R
import io.github.adccz.elias.data.notifications.NotificationChannels
import io.github.adccz.elias.domain.schedule.Prefetcher
import java.util.concurrent.TimeUnit

/**
 * Loads weather and headlines into the cache before the morning briefing (spec: 30 minutes before).
 *
 * WorkManager runs it as soon as there is a network, and tries again a few times when every source failed. The alarm
 * that enqueues it lets the app out of Doze for a moment, and expedited work starts right away.
 */
@HiltWorker
class PrefetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val prefetcher: Prefetcher,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = when {
        prefetcher.prefetch() -> Result.success()
        runAttemptCount + 1 >= MAX_ATTEMPTS -> Result.success()
        else -> Result.retry()
    }

    /** Expedited work runs as a foreground service on Android 11 and older, and that needs a notification. */
    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        NOTIFICATION_ID,
        NotificationCompat.Builder(applicationContext, NotificationChannels.PREFETCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.prefetch_notification))
            .setSilent(true)
            .build(),
    )

    companion object {
        private const val WORK_NAME = "prefetch"
        private const val NOTIFICATION_ID = 2001
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MINUTES = 5L

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PrefetchWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
