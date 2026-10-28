package io.github.adccz.elias

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.adccz.elias.core.coroutines.ApplicationScope
import io.github.adccz.elias.data.notifications.NotificationChannels
import io.github.adccz.elias.domain.schedule.ScheduleKeeper
import io.github.adccz.elias.domain.schedule.ScheduleRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Application entry point. [HiltAndroidApp] creates the app-wide dependency container.
 *
 * WorkManager is started on demand with Hilt's worker factory, so background jobs get their dependencies
 * injected like everything else. The default startup initializer is removed in the manifest for this.
 */
@HiltAndroidApp
class EliasApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var schedules: ScheduleRepository

    @Inject lateinit var scheduleKeeper: ScheduleKeeper

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        // The alarms are planned whenever the app's process starts (after an update too) and whenever the user
        // changes the schedule.
        applicationScope.launch {
            schedules.schedule.collect { scheduleKeeper.reschedule() }
        }
    }
}
