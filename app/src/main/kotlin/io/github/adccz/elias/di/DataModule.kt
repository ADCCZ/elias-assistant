package io.github.adccz.elias.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.data.news.RssNewsRepository
import io.github.adccz.elias.data.plan.AssetPlanRepository
import io.github.adccz.elias.data.weather.OpenMeteoWeatherRepository
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
}
