package io.github.adccz.elias.ui.journal

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.journal.Journal
import io.github.adccz.elias.domain.journal.JournalEntry
import io.github.adccz.elias.domain.journal.JournalText
import io.github.adccz.elias.testing.InMemoryJournal
import io.github.adccz.elias.testing.MainDispatcherExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    // Friday 30 October 2026, 0:30 in Prague: the entry still belongs to Thursday.
    private val clock = Clock.fixed(
        LocalDateTime.parse("2026-10-30T00:30").atZone(PRAGUE_ZONE).toInstant(),
        PRAGUE_ZONE,
    )
    private val thursday = LocalDate.of(2026, 10, 29)
    private val monday = LocalDate.of(2026, 10, 26)
    private val repository = InMemoryJournal(
        JournalEntry(monday, JournalText(thought = "Pondělní myšlenka"), Instant.EPOCH),
    )

    private fun TestScope.viewModel(): JournalViewModel {
        val viewModel = JournalViewModel(Journal(repository, clock), appScope = this)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `opens today's journal day with the older entries below`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(thursday, state.editingDate)
        assertTrue(state.isLoaded)
        assertNull(state.entry)
        assertEquals(listOf(monday), state.history.map { it.date })
    }

    @Test
    fun `saves shortly after typing stops, not on every letter`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTextChange(thursday, JournalText(thought = "Tic"))
        advanceTimeBy(JournalViewModel.SAVE_DELAY_MILLIS / 2)
        viewModel.onTextChange(thursday, JournalText(thought = "Ticho pomáhá"))
        advanceTimeBy(JournalViewModel.SAVE_DELAY_MILLIS / 2)
        runCurrent()
        assertNull(repository.stored(thursday))

        advanceUntilIdle()
        assertEquals("Ticho pomáhá", repository.stored(thursday)?.text?.thought)
        assertEquals("Ticho pomáhá", viewModel.uiState.value.entry?.text?.thought)
    }

    @Test
    fun `saves at once when the screen pauses`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTextChange(thursday, JournalText(toImprove = "Méně odkládat úkoly"))
        viewModel.saveNow()
        runCurrent()

        assertEquals("Méně odkládat úkoly", repository.stored(thursday)?.text?.toImprove)
    }

    @Test
    fun `opening an older day saves the current text first`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTextChange(thursday, JournalText(thought = "Dnešní"))
        viewModel.open(monday)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Dnešní", repository.stored(thursday)?.text?.thought)
        assertEquals(monday, state.editingDate)
        assertEquals("Pondělní myšlenka", state.entry?.text?.thought)
        assertEquals(listOf(thursday), state.history.map { it.date })
    }
}
