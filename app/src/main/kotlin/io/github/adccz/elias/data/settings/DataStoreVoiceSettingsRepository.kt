package io.github.adccz.elias.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.VoiceSettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Voice settings in Preferences DataStore: a small key-value file in the app's private storage,
 * read and written with coroutines (unlike SharedPreferences, it never blocks the main thread).
 */
class DataStoreVoiceSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    VoiceSettingsRepository {
    override val speechRate: Flow<SpeechRate> = dataStore.data
        // An unreadable file means "nothing stored yet": Elias speaks at normal speed instead of failing.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[SPEECH_RATE]?.let { SpeechRate.of(it) } ?: SpeechRate.NORMAL }
        .distinctUntilChanged()

    override suspend fun setSpeechRate(rate: SpeechRate) {
        try {
            dataStore.edit { it[SPEECH_RATE] = rate.multiplier }
        } catch (e: IOException) {
            // The phone could not write the file (full storage). The old rate stays, which is harmless.
        }
    }

    private companion object {
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
    }
}
