package io.github.adccz.elias.domain.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EveningStartTest {
    @Test
    fun `a sleeping phone lights up with the briefing screen`() {
        assertEquals(EveningStart.SCREEN, EveningStart.choose(canShowFullScreen = true, phoneInUse = false))
    }

    @Test
    fun `while the phone is in use the briefing starts as sound`() {
        assertEquals(EveningStart.SOUND, EveningStart.choose(canShowFullScreen = true, phoneInUse = true))
    }

    @Test
    fun `without full-screen notifications the briefing starts as sound`() {
        assertEquals(EveningStart.SOUND, EveningStart.choose(canShowFullScreen = false, phoneInUse = false))
    }
}
