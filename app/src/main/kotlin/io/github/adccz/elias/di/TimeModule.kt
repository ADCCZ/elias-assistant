package io.github.adccz.elias.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.core.time.PRAGUE_ZONE
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides the clock for the whole app.
 *
 * Code never calls `LocalDate.now()` on its own. It asks for a [Clock] instead,
 * so tests can pass `Clock.fixed(...)` and check any moment, such as midnight or a DST change.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.system(PRAGUE_ZONE)
}
