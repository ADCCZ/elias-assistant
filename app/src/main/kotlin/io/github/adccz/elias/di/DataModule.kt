package io.github.adccz.elias.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.data.calendar.AndroidCalendarSync
import io.github.adccz.elias.data.calendar.ContentResolverCalendarRepository
import io.github.adccz.elias.data.news.RssNewsRepository
import io.github.adccz.elias.data.plan.AssetPlanRepository
import io.github.adccz.elias.data.settings.DataStoreCalendarSettingsRepository
import io.github.adccz.elias.data.weather.OpenMeteoWeatherRepository
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.calendar.CalendarSettingsRepository
import io.github.adccz.elias.domain.calendar.CalendarSync
import io.github.adccz.elias.domain.calendar.SelectedCalendars
import io.github.adccz.elias.domain.news.NewsRepository
import io.github.adccz.elias.domain.plan.PlanRepository
import io.github.adccz.elias.domain.weather.WeatherRepository

/** Tells Hilt which data-layer class implements each domain interface. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindPlanRepository(impl: AssetPlanRepository): PlanRepository

    @Binds
    abstract fun bindWeatherRepository(impl: OpenMeteoWeatherRepository): WeatherRepository

    @Binds
    abstract fun bindNewsRepository(impl: RssNewsRepository): NewsRepository

    @Binds
    abstract fun bindCalendarSettingsRepository(impl: DataStoreCalendarSettingsRepository): CalendarSettingsRepository

    @Binds
    abstract fun bindCalendarSync(impl: AndroidCalendarSync): CalendarSync

    companion object {
        /** The phone's calendars without the ones switched off on Plány. Everything reads events through this. */
        @Provides
        fun provideCalendarRepository(
            phone: ContentResolverCalendarRepository,
            settings: CalendarSettingsRepository,
        ): CalendarRepository = SelectedCalendars(phone, settings)
    }
}
