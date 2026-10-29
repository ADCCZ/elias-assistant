package io.github.adccz.elias.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.adccz.elias.domain.topics.TopicSettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which calendars are topics, in the shared settings DataStore. Preferences have no set of numbers,
 * so the ids are stored as strings. A value that is not a number is ignored.
 */
class DataStoreTopicSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    TopicSettingsRepository {
    override val topicCalendarIds: Flow<Set<Long>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[KEY].orEmpty().mapNotNull { it.toLongOrNull() }.toSet() }
        .distinctUntilChanged()

    override suspend fun setTopic(calendarId: Long, isTopic: Boolean) {
        try {
            dataStore.edit { preferences ->
                val ids = preferences[KEY].orEmpty()
                preferences[KEY] = if (isTopic) ids + calendarId.toString() else ids - calendarId.toString()
            }
        } catch (e: IOException) {
            // The phone could not write the file (full storage). The old choice stays.
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("topic_calendar_ids")
    }
}
