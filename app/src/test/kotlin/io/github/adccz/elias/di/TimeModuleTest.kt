package io.github.adccz.elias.di

import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimeModuleTest {
    @Test
    fun `app clock runs in the Europe Prague zone`() {
        val clock = TimeModule.provideClock()

        assertEquals(ZoneId.of("Europe/Prague"), clock.zone)
    }
}
