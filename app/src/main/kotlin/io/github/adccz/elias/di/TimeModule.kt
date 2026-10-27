package io.github.adccz.elias.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.model.GeoLocation
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Provides time and place for the whole app.
 *
 * Code never calls `LocalDate.now()` on its own. It asks for a [Clock] instead,
 * so tests can pass `Clock.fixed(...)` and check any moment, such as midnight or a DST change.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun provideZoneId(): ZoneId = PRAGUE_ZONE

    @Provides
    @Singleton
    fun provideClock(zone: ZoneId): Clock = Clock.system(zone)

    @Provides
    fun provideHomeLocation(): GeoLocation = GeoLocation.PILSEN
}
