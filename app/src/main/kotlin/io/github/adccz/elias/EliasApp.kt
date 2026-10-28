package io.github.adccz.elias

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
