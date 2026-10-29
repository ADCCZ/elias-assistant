package io.github.adccz.elias.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.File
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
class DataStoreCalendarSettingsRepositoryTest {
    @TempDir
    lateinit var folder: File

    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(folder, "settings.preferences_pb") }
    }

    @AfterEach
    fun closeDataStore() = dataStoreScope.cancel()

    @Test
    fun `has no topics until the user marks one`() = runTest {
        assertEquals(emptySet<Long>(), DataStoreCalendarSettingsRepository(dataStore).topicCalendarIds.first())
    }

    @Test
    fun `adds and removes topic calendars`() = runTest {
        val repository = DataStoreCalendarSettingsRepository(dataStore)

        repository.setTopic(2, isTopic = true)
        repository.setTopic(4, isTopic = true)
        repository.setTopic(2, isTopic = false)

        assertEquals(setOf(4L), repository.topicCalendarIds.first())
    }

    @Test
    fun `ignores a stored value that is not a calendar id`() = runTest {
        dataStore.edit { it[stringSetPreferencesKey("topic_calendar_ids")] = setOf("7", "nonsense") }

        assertEquals(setOf(7L), DataStoreCalendarSettingsRepository(dataStore).topicCalendarIds.first())
    }

    @Test
    fun `reads every calendar until one is switched off, and again after it is switched on`() = runTest {
        val repository = DataStoreCalendarSettingsRepository(dataStore)
        assertEquals(emptySet<Long>(), repository.hiddenCalendarIds.first())

        repository.setRead(5, read = false)
        assertEquals(setOf(5L), repository.hiddenCalendarIds.first())

        repository.setRead(5, read = true)
        assertEquals(emptySet<Long>(), repository.hiddenCalendarIds.first())
        assertEquals(emptySet<Long>(), repository.topicCalendarIds.first())
    }
}
