package io.github.adccz.elias.ui.home

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeViewModelTest {
    private fun viewModelAt(instant: String) = HomeViewModel(Clock.fixed(Instant.parse(instant), PRAGUE_ZONE))

    @Test
    fun `today is the current date in Prague`() {
        val viewModel = viewModelAt("2026-10-27T09:00:00Z")

        assertEquals(LocalDate.of(2026, 10, 27), viewModel.uiState.value.today)
    }

    @Test
    fun `date changes at Prague midnight, not at UTC midnight`() {
        // 23:30 UTC on 27 October is already 00:30 on 28 October in Prague (winter time, UTC+1).
        val viewModel = viewModelAt("2026-10-27T23:30:00Z")

        assertEquals(LocalDate.of(2026, 10, 28), viewModel.uiState.value.today)
    }
}
