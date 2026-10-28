package io.github.adccz.elias.data.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a silent WAV file (16-bit PCM, mono) for the pause between two sections.
 * ExoPlayer plays it like any other clip, so pausing and resuming work the same during a pause.
 */
object SilenceWav {
    const val SAMPLE_RATE = 22_050
    const val HEADER_BYTES = 44

    private const val BYTES_PER_SAMPLE = 2
    private const val BITS_PER_SAMPLE = 16
    private const val FORMAT_PCM: Short = 1
    private const val MONO: Short = 1
    private const val FORMAT_CHUNK_BYTES = 16
    private const val MILLIS_PER_SECOND = 1_000

    /** The bytes of a WAV file with [millis] of silence. */
    fun create(millis: Long, sampleRate: Int = SAMPLE_RATE): ByteArray {
        require(millis >= 0) { "Silence cannot be negative: $millis ms" }
        val dataBytes = (sampleRate * millis / MILLIS_PER_SECOND).toInt() * BYTES_PER_SAMPLE
        return ByteBuffer.allocate(HEADER_BYTES + dataBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(HEADER_BYTES - 8 + dataBytes)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(FORMAT_CHUNK_BYTES)
            .putShort(FORMAT_PCM)
            .putShort(MONO)
            .putInt(sampleRate)
            .putInt(sampleRate * BYTES_PER_SAMPLE)
            .putShort(BYTES_PER_SAMPLE.toShort())
            .putShort(BITS_PER_SAMPLE.toShort())
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(dataBytes)
            // The samples stay zero, and zero is silence.
            .array()
    }
}
