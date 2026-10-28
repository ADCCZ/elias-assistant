package io.github.adccz.elias.domain.voice

import kotlinx.coroutines.flow.Flow

/** The user's voice settings, stored on the phone. */
interface VoiceSettingsRepository {
    /** The current speech rate. Emits again whenever it changes. */
    val speechRate: Flow<SpeechRate>

    suspend fun setSpeechRate(rate: SpeechRate)
}
