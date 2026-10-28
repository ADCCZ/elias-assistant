package io.github.adccz.elias.domain.voice

import kotlin.math.roundToInt

/**
 * How fast Elias speaks: a multiple of the voice's normal speed, from 0.7× to 1.5× in steps of 0.1.
 *
 * A value class: at runtime it is a plain Int, but the compiler keeps it apart from other numbers.
 * It counts tenths, so the 0.1 steps stay exact (0.1 has no exact Float value).
 */
@JvmInline
value class SpeechRate private constructor(private val tenths: Int) {
    /** The value for `TextToSpeech.setSpeechRate`: 1.0 is the normal speed. */
    val multiplier: Float get() = tenths / 10f

    /** About how many seconds [words] take to say at this rate. */
    fun secondsToSay(words: Int): Int = (words * SECONDS_PER_MINUTE / (WORDS_PER_MINUTE * multiplier)).roundToInt()

    companion object {
        private const val MIN_TENTHS = 7
        private const val MAX_TENTHS = 15

        /** Czech text-to-speech says about 150 words a minute at normal speed. */
        private const val WORDS_PER_MINUTE = 150.0
        private const val SECONDS_PER_MINUTE = 60

        val NORMAL = SpeechRate(10)
        val SLOWEST = SpeechRate(MIN_TENTHS)
        val FASTEST = SpeechRate(MAX_TENTHS)

        /** Values between the slowest and the fastest rate, for a slider with 0.1 steps. */
        const val STEPS_BETWEEN = MAX_TENTHS - MIN_TENTHS - 1

        /** Rounds [multiplier] to the nearest 0.1 and keeps it in range, so any stored or slider value is safe. */
        fun of(multiplier: Float): SpeechRate = if (multiplier.isNaN()) {
            NORMAL
        } else {
            SpeechRate((multiplier * 10).roundToInt().coerceIn(MIN_TENTHS, MAX_TENTHS))
        }
    }
}
