package io.github.adccz.elias.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.VoiceChoice
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
    // An unreadable file means "nothing stored yet": Elias speaks with the defaults instead of failing.
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    override val speechRate: Flow<SpeechRate> = preferences
        .map { preferences -> preferences[SPEECH_RATE]?.let { SpeechRate.of(it) } ?: SpeechRate.NORMAL }
        .distinctUntilChanged()

    override suspend fun setSpeechRate(rate: SpeechRate) = write { it[SPEECH_RATE] = rate.multiplier }

    override val voice: Flow<VoiceChoice> = preferences
        .map { preferences ->
            VoiceChoice.entries.firstOrNull { it.name == preferences[VOICE] } ?: VoiceChoice.BUILT_IN
        }
        .distinctUntilChanged()

    override suspend fun setVoice(choice: VoiceChoice) = write { it[VOICE] = choice.name }

    private suspend fun write(change: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(change)
        } catch (e: IOException) {
            // The phone could not write the file (full storage). The old value stays, which is harmless.
        }
    }

    private companion object {
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOICE = stringPreferencesKey("voice")
    }
}
