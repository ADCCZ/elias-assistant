package io.github.adccz.elias.data.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SilenceWavTest {
    private fun ByteArray.text(from: Int) = String(this, from, 4, Charsets.US_ASCII)

    private fun ByteArray.int(from: Int) = ByteBuffer.wrap(this, from, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun ByteArray.short(from: Int) = ByteBuffer.wrap(this, from, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

    @Test
    fun `writes a standard PCM WAV header`() {
        val wav = SilenceWav.create(millis = 700)

        assertEquals("RIFF", wav.text(0))
        assertEquals(wav.size - 8, wav.int(4))
        assertEquals("WAVE", wav.text(8))
        assertEquals("fmt ", wav.text(12))
        assertEquals(16, wav.int(16))
        assertEquals(1, wav.short(20)) // PCM
        assertEquals(1, wav.short(22)) // mono
        assertEquals(22_050, wav.int(24))
        assertEquals(44_100, wav.int(28)) // bytes per second
        assertEquals(2, wav.short(32)) // bytes per sample
        assertEquals(16, wav.short(34)) // bits per sample
        assertEquals("data", wav.text(36))
    }

    @Test
    fun `holds exactly the requested length of silence`() {
        val wav = SilenceWav.create(millis = 700)

        val samples = 22_050 * 7 / 10
        assertEquals(samples * 2, wav.int(40))
        assertEquals(SilenceWav.HEADER_BYTES + samples * 2, wav.size)
        assertTrue(wav.drop(SilenceWav.HEADER_BYTES).all { it == 0.toByte() })
    }

    @Test
    fun `zero milliseconds is a valid empty file`() {
        assertEquals(SilenceWav.HEADER_BYTES, SilenceWav.create(millis = 0).size)
    }

    @Test
    fun `negative length is a programming error`() {
        assertThrows<IllegalArgumentException> { SilenceWav.create(millis = -1) }
    }
}
