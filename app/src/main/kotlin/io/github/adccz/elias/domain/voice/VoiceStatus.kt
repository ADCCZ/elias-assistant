package io.github.adccz.elias.domain.voice

/** A text-to-speech engine installed on the phone, such as Google's or Samsung's. */
data class TtsEngine(val packageName: String, val label: String)

/** One voice an engine offers, reduced to what Elias needs for its decision. */
data class TtsVoice(
    val name: String,
    /** ISO 639 language code as the engine reports it: usually "cs", some engines use the three-letter "ces". */
    val language: String,
    val needsNetwork: Boolean,
    /** The voice is listed, but its data still has to be downloaded. */
    val notInstalled: Boolean,
    /** The engine's own rating (`Voice.QUALITY_*`), higher is better. */
    val quality: Int,
    /** The voice chosen in the phone's text-to-speech settings. */
    val isDefault: Boolean = false,
)

/** Whether Elias can speak Czech on this phone, and if not, what the user has to fix. */
sealed interface VoiceStatus {
    /** Elias speaks offline with [voiceName]. */
    data class Ready(val engine: TtsEngine, val voiceName: String) : VoiceStatus

    /** Text-to-speech did not start: no engine is installed, or it failed to start. */
    data object EngineUnavailable : VoiceStatus

    /** The phone's default engine has no Czech voice. The fix is another engine, such as Google's. */
    data class NoCzech(val engine: TtsEngine) : VoiceStatus

    /** An offline Czech voice exists, but its data is not downloaded yet. */
    data class NotDownloaded(val engine: TtsEngine) : VoiceStatus

    /**
     * Czech works only with a network voice. Elias never uses one: the engine would send the whole briefing
     * (calendar, plan and from M5 the names of people who wrote overnight) to a server.
     */
    data class OnlineOnly(val engine: TtsEngine) : VoiceStatus
}

/** Checks whether the phone can speak Czech offline. Implemented in the data layer with Android's TextToSpeech. */
interface VoiceChecker {
    suspend fun check(): VoiceStatus
}

/** Decides the [VoiceStatus] from what the engine reports. Pure logic, so every case is unit-tested. */
object VoiceStatusEvaluator {
    private val czechCodes = setOf("cs", "ces", "cze")

    /** [engine] is null when text-to-speech did not start. */
    fun evaluate(engine: TtsEngine?, voices: List<TtsVoice>): VoiceStatus {
        if (engine == null) return VoiceStatus.EngineUnavailable
        val czech = voices.filter { it.language.lowercase() in czechCodes }
        val offline = czech.filter { !it.needsNetwork }
        val best = offline
            .filter { !it.notInstalled }
            // The user's own choice first, then the best quality. The name only makes the choice repeatable.
            .maxWithOrNull(compareBy<TtsVoice>({ it.isDefault }, { it.quality }, { it.name }))
        return when {
            best != null -> VoiceStatus.Ready(engine, best.name)
            offline.isNotEmpty() -> VoiceStatus.NotDownloaded(engine)
            czech.isNotEmpty() -> VoiceStatus.OnlineOnly(engine)
            else -> VoiceStatus.NoCzech(engine)
        }
    }
}
