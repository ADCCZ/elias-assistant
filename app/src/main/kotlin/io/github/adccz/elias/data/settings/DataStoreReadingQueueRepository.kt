package io.github.adccz.elias.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.reading.ReadingQueue
import io.github.adccz.elias.domain.reading.ReadingQueueRepository
import io.github.adccz.elias.domain.reading.ReadingSection
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The reading queue in the shared settings DataStore, as one small JSON value (books by their ČEP abbreviation).
 * A section that no longer makes sense (an unknown book, a chapter out of range) is left out, and a broken value
 * reads as an empty queue, so the app always starts.
 */
class DataStoreReadingQueueRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    ReadingQueueRepository {
    override val queue: Flow<ReadingQueue> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { decode(it[QUEUE]) }
        .distinctUntilChanged()

    override suspend fun update(change: (ReadingQueue) -> ReadingQueue): ReadingQueue? = try {
        var updated: ReadingQueue? = null
        dataStore.edit { preferences ->
            val changed = change(decode(preferences[QUEUE]))
            preferences[QUEUE] = encode(changed)
            updated = changed
        }
        updated
    } catch (e: IOException) {
        // The phone could not write the file (full storage). The old queue stays.
        null
    }

    internal companion object {
        val QUEUE = stringPreferencesKey("reading_queue")

        private val json = Json { ignoreUnknownKeys = true }

        fun encode(queue: ReadingQueue): String = json.encodeToString(
            StoredQueue.serializer(),
            StoredQueue(
                sections = queue.sections.map { StoredSection(it.id, it.book.abbreviation, it.next, it.last) },
                finished = queue.finished,
            ),
        )

        fun decode(text: String?): ReadingQueue {
            if (text == null) return ReadingQueue()
            val stored = try {
                json.decodeFromString(StoredQueue.serializer(), text)
            } catch (e: SerializationException) {
                return ReadingQueue()
            } catch (e: IllegalArgumentException) {
                return ReadingQueue()
            }
            val sections = stored.sections.mapNotNull { section ->
                val book = BibleBooks.byAbbreviation(section.book) ?: return@mapNotNull null
                if (section.next !in 1..section.last || section.last > book.chapters) return@mapNotNull null
                ReadingSection(section.id, book, section.next, section.last)
            }
            return ReadingQueue(sections, stored.finished)
        }
    }
}

@Serializable
internal class StoredQueue(val sections: List<StoredSection> = emptyList(), val finished: Boolean = false)

@Serializable
internal class StoredSection(val id: Long, val book: String, val next: Int, val last: Int)
