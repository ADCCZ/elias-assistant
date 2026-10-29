package io.github.adccz.elias.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.VoiceChoice
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

/** Runs the real DataStore on a file in a temporary folder, as the DataStore library's own tests do. */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreVoiceSettingsRepositoryTest {
    @TempDir
    lateinit var folder: File

    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(folder, "settings.preferences_pb") }
    }

    @AfterEach
    fun closeDataStore() = dataStoreScope.cancel()

    @Test
    fun `speaks at normal speed when nothing is stored`() = runTest {
        assertEquals(SpeechRate.NORMAL, DataStoreVoiceSettingsRepository(dataStore).speechRate.first())
    }

    @Test
    fun `remembers the speech rate`() = runTest {
        val repository = DataStoreVoiceSettingsRepository(dataStore)

        repository.setSpeechRate(SpeechRate.of(1.3f))

        assertEquals(SpeechRate.of(1.3f), repository.speechRate.first())
    }

    @Test
    fun `brings a stored value back into range`() = runTest {
        dataStore.edit { it[floatPreferencesKey("speech_rate")] = 9f }

        assertEquals(SpeechRate.FASTEST, DataStoreVoiceSettingsRepository(dataStore).speechRate.first())
    }

    @Test
    fun `speaks with the app's own voice when nothing is stored`() = runTest {
        assertEquals(VoiceChoice.BUILT_IN, DataStoreVoiceSettingsRepository(dataStore).voice.first())
    }

    @Test
    fun `remembers the voice choice`() = runTest {
        val repository = DataStoreVoiceSettingsRepository(dataStore)

        repository.setVoice(VoiceChoice.PHONE)

        assertEquals(VoiceChoice.PHONE, repository.voice.first())
    }
}
