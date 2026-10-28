package io.github.adccz.elias.data.voice

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioFocusPolicyTest {
    /** A clock the test moves forward by hand. */
    private class TestClock(var now: Instant = Instant.parse("2026-10-28T17:30:00Z")) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        fun advance(duration: Duration) {
            now += duration
        }
    }

    private val clock = TestClock()
    private val policy = AudioFocusPolicy(clock)

    /** Starts playing with granted focus, as after a tap on Přehrát. */
    private fun startPlaying() {
        assertTrue(policy.onPlayRequested())
        policy.onFocusRequestResult(granted = true)
    }

    @Test
    fun `asks for focus once and then keeps it while playing`() {
        startPlaying()

        assertTrue(policy.holdsFocus)
        assertFalse(policy.onPlayRequested())
    }

    @Test
    fun `refused focus does not count as held`() {
        assertTrue(policy.onPlayRequested())
        policy.onFocusRequestResult(granted = false)

        assertFalse(policy.holdsFocus)
        assertTrue(policy.onPlayRequested())
    }

    @Test
    fun `a phone call pauses and the end of the call resumes`() {
        startPlaying()

        assertEquals(FocusAction.PAUSE, policy.onFocusChange(FocusChange.LOST_TRANSIENTLY, wantsToPlay = true))
        assertFalse(policy.onPaused()) // its own pause keeps the focus
        clock.advance(Duration.ofMinutes(3))

        assertEquals(FocusAction.RESUME, policy.onFocusChange(FocusChange.GAINED, wantsToPlay = false))
        assertTrue(policy.holdsFocus)
    }

    @Test
    fun `after a call longer than ten minutes it stays paused and gives the focus back`() {
        startPlaying()
        policy.onFocusChange(FocusChange.LOST_TRANSIENTLY, wantsToPlay = true)
        policy.onPaused()
        clock.advance(Duration.ofMinutes(11))

        assertEquals(FocusAction.ABANDON, policy.onFocusChange(FocusChange.GAINED, wantsToPlay = false))
        assertFalse(policy.holdsFocus)
        assertTrue(policy.onPlayRequested())
    }

    @Test
    fun `an interruption while paused by the user changes nothing`() {
        startPlaying()
        assertTrue(policy.onPaused()) // the user paused: focus goes back

        assertEquals(FocusAction.NONE, policy.onFocusChange(FocusChange.LOST_TRANSIENTLY, wantsToPlay = false))
        assertEquals(FocusAction.NONE, policy.onFocusChange(FocusChange.GAINED, wantsToPlay = false))
    }

    @Test
    fun `play during a call asks again and still resumes after the call`() {
        startPlaying()
        policy.onFocusChange(FocusChange.LOST_TRANSIENTLY, wantsToPlay = true)
        policy.onPaused()

        assertTrue(policy.onPlayRequested())
        policy.onFocusRequestResult(granted = false) // Android refuses focus during a call

        assertEquals(FocusAction.RESUME, policy.onFocusChange(FocusChange.GAINED, wantsToPlay = false))
    }

    @Test
    fun `losing the focus for good stops Elias`() {
        startPlaying()

        assertEquals(FocusAction.STOP, policy.onFocusChange(FocusChange.LOST, wantsToPlay = true))
        assertFalse(policy.holdsFocus)
        assertFalse(policy.onStopped()) // nothing left to give back
    }

    @Test
    fun `the end of the briefing gives the focus back once`() {
        startPlaying()

        assertTrue(policy.onStopped())
        assertFalse(policy.onStopped())
    }

    @Test
    fun `stopping during a call does not resume afterwards`() {
        startPlaying()
        policy.onFocusChange(FocusChange.LOST_TRANSIENTLY, wantsToPlay = true)
        policy.onPaused()

        assertTrue(policy.onStopped())
        assertEquals(FocusAction.NONE, policy.onFocusChange(FocusChange.GAINED, wantsToPlay = false))
    }
}
