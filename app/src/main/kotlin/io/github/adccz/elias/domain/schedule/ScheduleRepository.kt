package io.github.adccz.elias.domain.schedule

import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

/** The weekly schedule the user set, stored on the phone. */
interface ScheduleRepository {
    /** The current schedule. Emits again whenever a day changes. */
    val schedule: Flow<WeeklySchedule>

    suspend fun setDay(day: DayOfWeek, times: DayTimes)
}
