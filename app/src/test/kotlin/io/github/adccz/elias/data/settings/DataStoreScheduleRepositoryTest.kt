package io.github.adccz.elias.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.adccz.elias.domain.schedule.DayTimes
import io.github.adccz.elias.domain.schedule.WeeklySchedule
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreScheduleRepositoryTest {
    @TempDir
    lateinit var folder: File

    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(folder, "settings.preferences_pb") }
    }

    @AfterEach
    fun closeDataStore() = dataStoreScope.cancel()

    @Test
    fun `uses the default times when nothing is stored`() = runTest {
        assertEquals(WeeklySchedule.DEFAULT, DataStoreScheduleRepository(dataStore).schedule.first())
    }

    @Test
    fun `remembers the times of one day without touching the others`() = runTest {
        val repository = DataStoreScheduleRepository(dataStore)
        val wednesday = DayTimes(morning = LocalTime.of(6, 45), evening = LocalTime.of(21, 30))

        repository.setDay(DayOfWeek.WEDNESDAY, wednesday)

        assertEquals(WeeklySchedule.DEFAULT.with(DayOfWeek.WEDNESDAY, wednesday), repository.schedule.first())
    }

    @Test
    fun `remembers a time that is switched off`() = runTest {
        val repository = DataStoreScheduleRepository(dataStore)

        repository.setDay(DayOfWeek.SUNDAY, DayTimes(morning = null, evening = LocalTime.of(22, 0)))

        assertEquals(null, repository.schedule.first().on(DayOfWeek.SUNDAY).morning)
    }

    @Test
    fun `keeps minutes exact, including midnight`() = runTest {
        val repository = DataStoreScheduleRepository(dataStore)

        repository.setDay(DayOfWeek.MONDAY, DayTimes(morning = LocalTime.MIDNIGHT, evening = LocalTime.of(23, 59)))

        assertEquals(
            DayTimes(LocalTime.MIDNIGHT, LocalTime.of(23, 59)),
            repository.schedule.first().on(DayOfWeek.MONDAY),
        )
    }

    @Test
    fun `falls back to the default for a value out of range`() = runTest {
        dataStore.edit { it[intPreferencesKey("evening_monday")] = 5000 }

        val monday = DataStoreScheduleRepository(dataStore).schedule.first().on(DayOfWeek.MONDAY)

        assertEquals(WeeklySchedule.DEFAULT_EVENING, monday.evening)
    }
}
