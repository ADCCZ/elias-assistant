package io.github.adccz.elias.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.adccz.elias.domain.schedule.DayTimes
import io.github.adccz.elias.domain.schedule.ScheduleRepository
import io.github.adccz.elias.domain.schedule.WeeklySchedule
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The weekly schedule in the shared settings DataStore. Each time is stored as minutes after midnight
 * under its own key ("evening_friday"), [OFF] when the user switched it off. A missing or broken value
 * falls back to the default time, so Elias always has a usable schedule.
 */
class DataStoreScheduleRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    ScheduleRepository {
    override val schedule: Flow<WeeklySchedule> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            WeeklySchedule(
                DayOfWeek.entries.associateWith { day ->
                    DayTimes(
                        morning = preferences.timeOrDefault(morningKey(day), WeeklySchedule.DEFAULT_MORNING),
                        evening = preferences.timeOrDefault(eveningKey(day), WeeklySchedule.DEFAULT_EVENING),
                    )
                },
            )
        }
        .distinctUntilChanged()

    override suspend fun setDay(day: DayOfWeek, times: DayTimes) {
        try {
            dataStore.edit {
                it[morningKey(day)] = times.morning.toStored()
                it[eveningKey(day)] = times.evening.toStored()
            }
        } catch (e: IOException) {
            // The phone could not write the file (full storage). The old times stay and stay scheduled.
        }
    }

    private fun Preferences.timeOrDefault(key: Preferences.Key<Int>, default: LocalTime): LocalTime? =
        when (val minutes = this[key]) {
            null -> default
            OFF -> null
            in 0 until MINUTES_PER_DAY -> LocalTime.ofSecondOfDay(minutes.toLong() * SECONDS_PER_MINUTE)
            else -> default
        }

    private fun LocalTime?.toStored(): Int = this?.let { it.toSecondOfDay() / SECONDS_PER_MINUTE } ?: OFF

    private companion object {
        const val OFF = -1
        const val MINUTES_PER_DAY = 24 * 60
        const val SECONDS_PER_MINUTE = 60

        fun morningKey(day: DayOfWeek) = intPreferencesKey("morning_${day.name.lowercase()}")

        fun eveningKey(day: DayOfWeek) = intPreferencesKey("evening_${day.name.lowercase()}")
    }
}
