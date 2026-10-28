package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingSection

/** Turns briefing sections into audio files on the phone. Implemented with Android's TextToSpeech. */
interface SpeechSynthesizer {
    /** One clip per section, in the given order, spoken offline at [rate]. Never throws for engine problems. */
    suspend fun synthesize(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult
}

sealed interface SynthesisResult {
    data class Success(val clips: List<SpeechClip>) : SynthesisResult

    /** There is no offline Czech voice. [status] says what the user has to fix. */
    data class VoiceNotReady(val status: VoiceStatus) : SynthesisResult

    /** The engine failed while speaking. [detail] is technical and never shown as is. */
    data class Failure(val detail: String) : SynthesisResult
}
