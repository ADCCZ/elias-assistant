package io.github.adccz.elias.domain.voice

import io.github.adccz.elias.domain.briefing.BriefingSection
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.flow.first

/** Marks the phone's text-to-speech engine, as opposed to the app's own voice. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PhoneVoice

/**
 * The app's own offline voice ("Oldřich 3.0"). It is part of the app, but a build without the voice model
 * (CI, D38) has none: then [status] is null.
 */
interface BuiltInVoice : SpeechSynthesizer {
    /** Ready to speak, or null when this build has no voice model or the voice crashed the app before. */
    val status: VoiceStatus?

    /**
     * True when the voice's native code crashed the app during an earlier briefing. The voice then stays off until
     * the app is updated, so the same crash cannot silence every evening.
     */
    val crashedBefore: Boolean
}

/**
 * Speaks with the voice the user chose and falls back to the phone's voice whenever the app's own voice is missing
 * or fails, so a briefing never stays silent because of the new voice. Both voices work offline only (D25).
 */
class SelectedVoice @Inject constructor(
    private val builtIn: BuiltInVoice,
    @param:PhoneVoice private val phone: SpeechSynthesizer,
    @param:PhoneVoice private val phoneChecker: VoiceChecker,
    private val settings: VoiceSettingsRepository,
) : SpeechSynthesizer,
    VoiceChecker {
    override suspend fun synthesize(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult {
        if (useBuiltIn()) {
            val result = builtIn.synthesize(sections, rate)
            if (result is SynthesisResult.Success) return result
        }
        return phone.synthesize(sections, rate)
    }

    override suspend fun check(): VoiceStatus =
        if (useBuiltIn()) builtIn.status ?: phoneChecker.check() else phoneChecker.check()

    private suspend fun useBuiltIn(): Boolean = settings.voice.first() == VoiceChoice.BUILT_IN && builtIn.status != null
}
