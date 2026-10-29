package io.github.adccz.elias.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.adccz.elias.domain.calendar.CalendarSettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The calendar choices in the shared settings DataStore: the topic calendars and the calendars not to read.
 * Preferences have no set of numbers, so the ids are stored as strings. A value that is not a number is ignored.
 */
class DataStoreCalendarSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    CalendarSettingsRepository {
    override val topicCalendarIds: Flow<Set<Long>> = ids(TOPICS)

    override val hiddenCalendarIds: Flow<Set<Long>> = ids(HIDDEN)

    override suspend fun setTopic(calendarId: Long, isTopic: Boolean) = update(TOPICS, calendarId, isTopic)

    override suspend fun setRead(calendarId: Long, read: Boolean) = update(HIDDEN, calendarId, !read)

    private fun ids(key: Preferences.Key<Set<String>>): Flow<Set<Long>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[key].orEmpty().mapNotNull { it.toLongOrNull() }.toSet() }
        .distinctUntilChanged()

    private suspend fun update(key: Preferences.Key<Set<String>>, calendarId: Long, include: Boolean) {
        try {
            dataStore.edit { preferences ->
                val ids = preferences[key].orEmpty()
                preferences[key] = if (include) ids + calendarId.toString() else ids - calendarId.toString()
            }
        } catch (e: IOException) {
            // The phone could not write the file (full storage). The old choice stays.
        }
    }

    private companion object {
        val TOPICS = stringSetPreferencesKey("topic_calendar_ids")
        val HIDDEN = stringSetPreferencesKey("hidden_calendar_ids")
    }
}
