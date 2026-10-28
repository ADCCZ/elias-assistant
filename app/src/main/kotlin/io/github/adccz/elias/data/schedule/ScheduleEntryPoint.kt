package io.github.adccz.elias.data.schedule

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.adccz.elias.core.coroutines.ApplicationScope
import io.github.adccz.elias.domain.schedule.ScheduleKeeper
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope

/**
 * What the broadcast receivers need from Hilt. Receivers are created by Android, so they ask for their
 * dependencies through this entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleEntryPoint {
    fun scheduleKeeper(): ScheduleKeeper

    fun clock(): Clock

    fun zone(): ZoneId

    @ApplicationScope
    fun applicationScope(): CoroutineScope

    companion object {
        fun from(context: Context): ScheduleEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ScheduleEntryPoint::class.java)
    }
}
