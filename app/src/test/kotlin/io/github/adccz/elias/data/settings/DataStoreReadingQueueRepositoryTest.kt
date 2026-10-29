package io.github.adccz.elias.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.reading.ReadingQueue
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreReadingQueueRepositoryTest {
    @TempDir
    lateinit var folder: File

    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(folder, "settings.preferences_pb") }
    }

    @AfterEach
    fun closeDataStore() = dataStoreScope.cancel()

    private val mark = BibleBooks.byAbbreviation("Mk")!!
    private val firstCorinthians = BibleBooks.byAbbreviation("1 K")!!

    @Test
    fun `starts with an empty queue`() = runTest {
        assertEquals(ReadingQueue(), DataStoreReadingQueueRepository(dataStore).queue.first())
    }

    @Test
    fun `keeps the sections, their progress and the order`() = runTest {
        val repository = DataStoreReadingQueueRepository(dataStore)
        val queue = ReadingQueue().add(firstCorinthians, 13, 13).add(mark).advance()
        val withMark = queue.add(mark).advance()

        val saved = repository.update { withMark }

        assertEquals(withMark, saved)
        assertEquals(withMark, repository.queue.first())
        // A new repository on the same file: what survives a restart of the app.
        assertEquals(withMark, DataStoreReadingQueueRepository(dataStore).queue.first())
    }

    @Test
    fun `remembers that the queue was read to its end`() = runTest {
        val repository = DataStoreReadingQueueRepository(dataStore)

        repository.update { ReadingQueue().add(mark, 16, 16).advance() }

        assertTrue(repository.queue.first().finished)
    }

    @Test
    fun `an update works on what is stored`() = runTest {
        val repository = DataStoreReadingQueueRepository(dataStore)
        repository.update { it.add(mark) }

        repository.update { it.advance() }
        repository.update { it.advance() }

        assertEquals("Marek 3–16", repository.queue.first().sections.single().label)
    }

    @Test
    fun `leaves out sections that make no sense and reads broken data as an empty queue`() = runTest {
        val repository = DataStoreReadingQueueRepository(dataStore)
        dataStore.edit {
            it[DataStoreReadingQueueRepository.QUEUE] =
                """{"sections":[{"id":1,"book":"Tob","next":1,"last":3},{"id":2,"book":"Mk","next":5,"last":17},""" +
                """{"id":3,"book":"Mk","next":2,"last":16}]}"""
        }

        assertEquals(listOf("Marek 2–16"), repository.queue.first().sections.map { it.label })

        dataStore.edit { it[DataStoreReadingQueueRepository.QUEUE] = "{not json" }

        assertEquals(ReadingQueue(), repository.queue.first())
    }
}
