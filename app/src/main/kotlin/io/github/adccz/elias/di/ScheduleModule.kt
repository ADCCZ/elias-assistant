package io.github.adccz.elias.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.data.schedule.AndroidAlarmScheduler
import io.github.adccz.elias.data.settings.DataStoreScheduleRepository
import io.github.adccz.elias.data.status.AndroidSystemChecks
import io.github.adccz.elias.domain.schedule.AlarmScheduler
import io.github.adccz.elias.domain.schedule.ScheduleRepository
import io.github.adccz.elias.domain.status.SystemChecks

/** Scheduling: when Elias works on its own. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleModule {
    @Binds
    abstract fun bindScheduleRepository(impl: DataStoreScheduleRepository): ScheduleRepository

    @Binds
    abstract fun bindAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler

    @Binds
    abstract fun bindSystemChecks(impl: AndroidSystemChecks): SystemChecks
}
