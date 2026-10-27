package io.github.adccz.elias.domain.sun

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.model.GeoLocation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SabbathCalculatorTest {
    private val sun = SunCalculator(GeoLocation.PILSEN, PRAGUE_ZONE)
    private val calculator = SabbathCalculator(sun)

    private fun at(text: String) = LocalDateTime.parse(text).atZone(PRAGUE_ZONE)

    @Test
    fun `on a weekday returns the coming sabbath`() {
        val window = calculator.currentOrNext(at("2026-10-27T10:00")) // Tuesday

        assertEquals(LocalDate.of(2026, 10, 30), window.start.toLocalDate())
        assertEquals(DayOfWeek.FRIDAY, window.start.dayOfWeek)
        assertEquals(sun.sunTimes(LocalDate.of(2026, 10, 30)).sunset, window.start)
        assertEquals(sun.sunTimes(LocalDate.of(2026, 10, 31)).sunset, window.end)
    }

    @Test
    fun `on Friday afternoon before sunset returns that evening's sabbath`() {
        val now = at("2026-10-30T15:00")

        val window = calculator.currentOrNext(now)

        assertEquals(LocalDate.of(2026, 10, 30), window.start.toLocalDate())
        assertFalse(now in window)
    }

    @Test
    fun `on Saturday before sunset the sabbath is in progress`() {
        val now = at("2026-10-31T12:00")

        val window = calculator.currentOrNext(now)

        assertEquals(LocalDate.of(2026, 10, 30), window.start.toLocalDate())
        assertTrue(now in window)
    }

    @Test
    fun `on Saturday after sunset returns next week's sabbath`() {
        val window = calculator.currentOrNext(at("2026-10-31T18:00"))

        assertEquals(LocalDate.of(2026, 11, 6), window.start.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 7), window.end.toLocalDate())
    }
}
