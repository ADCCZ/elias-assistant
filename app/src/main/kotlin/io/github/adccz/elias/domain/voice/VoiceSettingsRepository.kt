package io.github.adccz.elias.domain.voice

import kotlinx.coroutines.flow.Flow

/** Which voice Elias speaks with. */
enum class VoiceChoice {
    /** The app's own offline male voice, "Oldřich 3.0" (the Piper voice "Jirka"). The default. */
    BUILT_IN,

    /** The offline Czech voice of the phone's text-to-speech engine (Google or Samsung). */
    PHONE,
}

/** The user's voice settings, stored on the phone. */
interface VoiceSettingsRepository {
    /** The current speech rate. Emits again whenever it changes. */
    val speechRate: Flow<SpeechRate>

    suspend fun setSpeechRate(rate: SpeechRate)

    /** The chosen voice. Emits again whenever it changes. */
    val voice: Flow<VoiceChoice>

    suspend fun setVoice(choice: VoiceChoice)
}
