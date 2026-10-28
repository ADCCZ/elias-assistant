package io.github.adccz.elias.domain.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SpeechRateTest {
    @ParameterizedTest(name = "{0} becomes {1}")
    @CsvSource(
        "1.0, 1.0",
        "1.04, 1.0",
        "1.06, 1.1",
        "0.7, 0.7",
        "1.5, 1.5",
        "0.2, 0.7",
        "3.0, 1.5",
    )
    fun `rounds to a tenth and stays in range`(input: Float, expected: Float) {
        assertEquals(expected, SpeechRate.of(input).multiplier)
    }

    @Test
    fun `an invalid stored value means the normal rate`() {
        assertEquals(SpeechRate.NORMAL, SpeechRate.of(Float.NaN))
    }

    @Test
    fun `the same rate from a slider and from storage is equal`() {
        // 0.1 steps added as floats drift (0.7 + 0.1 + 0.1 = 0.90000004), the rate does not.
        assertEquals(SpeechRate.of(0.9f), SpeechRate.of(0.7f + 0.1f + 0.1f))
    }

    @Test
    fun `a slider between the slowest and the fastest rate has seven steps`() {
        assertEquals(0.7f, SpeechRate.SLOWEST.multiplier)
        assertEquals(1.5f, SpeechRate.FASTEST.multiplier)
        assertEquals(7, SpeechRate.STEPS_BETWEEN)
    }

    @Test
    fun `a faster voice needs less time`() {
        assertEquals(60, SpeechRate.NORMAL.secondsToSay(150))
        assertEquals(40, SpeechRate.FASTEST.secondsToSay(150))
        assertEquals(86, SpeechRate.SLOWEST.secondsToSay(150))
    }
}
