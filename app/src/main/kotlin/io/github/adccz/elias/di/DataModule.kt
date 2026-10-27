package io.github.adccz.elias.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.data.plan.AssetPlanRepository
import io.github.adccz.elias.domain.plan.PlanRepository

/** Tells Hilt which data-layer class implements each domain interface. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindPlanRepository(impl: AssetPlanRepository): PlanRepository
}
