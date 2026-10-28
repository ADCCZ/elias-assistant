package io.github.adccz.elias.domain.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VoiceStatusEvaluatorTest {
    private val google = TtsEngine("com.example.tts", "Převod textu na řeč")

    private fun voice(
        name: String,
        language: String = "cs",
        needsNetwork: Boolean = false,
        notInstalled: Boolean = false,
        quality: Int = 300,
        isDefault: Boolean = false,
    ) = TtsVoice(name, language, needsNetwork, notInstalled, quality, isDefault)

    private fun evaluate(vararg voices: TtsVoice) = VoiceStatusEvaluator.evaluate(google, voices.toList())

    @Test
    fun `no engine means text-to-speech is unavailable`() {
        assertEquals(VoiceStatus.EngineUnavailable, VoiceStatusEvaluator.evaluate(null, emptyList()))
    }

    @Test
    fun `an engine without Czech needs another engine`() {
        val status = evaluate(voice("en-us-local", language = "en"), voice("sk-local", language = "sk"))

        assertEquals(VoiceStatus.NoCzech(google), status)
    }

    @Test
    fun `a network voice alone is not used`() {
        assertEquals(VoiceStatus.OnlineOnly(google), evaluate(voice("cs-network", needsNetwork = true)))
    }

    @Test
    fun `an offline voice that is not downloaded asks for the download`() {
        val status = evaluate(voice("cs-network", needsNetwork = true), voice("cs-local", notInstalled = true))

        assertEquals(VoiceStatus.NotDownloaded(google), status)
    }

    @Test
    fun `an installed offline voice is ready even next to a network voice`() {
        val status = evaluate(voice("cs-network", needsNetwork = true, quality = 500), voice("cs-local"))

        assertEquals(VoiceStatus.Ready(google, "cs-local"), status)
    }

    @Test
    fun `prefers the voice chosen in the phone settings over a better one`() {
        val status = evaluate(voice("cs-a-local", quality = 500), voice("cs-b-local", quality = 300, isDefault = true))

        assertEquals(VoiceStatus.Ready(google, "cs-b-local"), status)
    }

    @Test
    fun `otherwise prefers the better quality`() {
        val status = evaluate(voice("cs-a-local", quality = 300), voice("cs-b-local", quality = 400))

        assertEquals(VoiceStatus.Ready(google, "cs-b-local"), status)
    }

    @Test
    fun `recognizes the three-letter language code`() {
        assertEquals(VoiceStatus.Ready(google, "ces-local"), evaluate(voice("ces-local", language = "CES")))
    }
}
