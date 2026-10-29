package io.github.adccz.elias.ui.reading

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.plan.DatedText
import io.github.adccz.elias.domain.reading.BibleReading
import io.github.adccz.elias.domain.reading.ReadingQueue
import io.github.adccz.elias.testing.FixedPlans
import io.github.adccz.elias.testing.InMemoryReadingLog
import io.github.adccz.elias.testing.InMemoryReadingQueue
import io.github.adccz.elias.testing.MainDispatcherExtension
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val mark = BibleBooks.byAbbreviation("Mk")!!
    private val acts = BibleBooks.byAbbreviation("Sk")!!
    private val thursday = LocalDate.of(2026, 10, 29)
    private val clock = Clock.fixed(
        LocalDateTime.parse("2026-10-29T07:00").atZone(PRAGUE_ZONE).toInstant(),
        PRAGUE_ZONE,
    )

    private val queue = InMemoryReadingQueue(ReadingQueue().add(mark, 3, 16).add(acts))
    private val log = InMemoryReadingLog()
    private var planReadings: List<DatedText>? = null

    private val viewModel by lazy {
        ReadingViewModel(BibleReading(queue, log, FixedPlans(planReadings), clock))
    }

    /** Keeps the state flow running, like a visible screen does. */
    private fun TestScope.watch() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `shows the queue and today's chapter`() = runTest {
        watch()
        advanceUntilIdle()

        assertTrue(state.isLoaded)
        assertEquals(listOf("Marek 3–16", "Skutky apoštolů"), state.sections.map { it.label })
        assertEquals(TodayReading(next = Chapter(mark, 3)), state.today)
    }

    @Test
    fun `Přečteno moves on and offers to take it back`() = runTest {
        watch()

        viewModel.markRead()
        advanceUntilIdle()

        assertEquals(TodayReading(next = Chapter(mark, 4), readToday = listOf(Chapter(mark, 3))), state.today)
        val message = state.message as ReadingMessage.Marked
        assertEquals(Chapter(mark, 3), message.mark.chapter)

        viewModel.undo(message.mark)
        viewModel.messageShown(message)
        advanceUntilIdle()

        assertEquals(TodayReading(next = Chapter(mark, 3)), state.today)
        assertNull(state.message)
    }

    @Test
    fun `a message that was shown does not hide a newer one`() = runTest {
        watch()
        viewModel.markRead()
        advanceUntilIdle()
        val first = state.message!!
        viewModel.markRead()
        advanceUntilIdle()

        viewModel.messageShown(first)

        assertEquals(Chapter(mark, 4), (state.message as ReadingMessage.Marked).mark.chapter)
    }

    @Test
    fun `edits the queue`() = runTest {
        watch()
        val actsId = queue.current.sections[1].id

        viewModel.add(BibleBooks.byAbbreviation("J")!!, 13, 13)
        viewModel.move(actsId, -1)
        advanceUntilIdle()
        assertEquals(listOf("Skutky apoštolů", "Marek 3–16", "Jan 13"), state.sections.map { it.label })

        viewModel.remove(actsId)
        advanceUntilIdle()
        viewModel.continueWith(queue.current.sections[1].id, 13)
        advanceUntilIdle()
        assertEquals(listOf("Jan 13", "Marek 3–16"), state.sections.map { it.label })
    }

    @Test
    fun `ignores chapters outside the book or the section`() = runTest {
        watch()
        val before = queue.current

        viewModel.add(acts, 0, 5)
        viewModel.add(acts, 5, 29)
        viewModel.add(acts, 6, 5)
        viewModel.continueWith(before.sections[0].id, 17)
        advanceUntilIdle()

        assertEquals(before, queue.current)
    }

    @Test
    fun `takes the plan's readings over into an empty queue`() = runTest {
        queue.update { ReadingQueue() }
        planReadings = listOf(DatedText(thursday, "J 1"), DatedText(thursday.plusDays(1), "J 2"))
        watch()

        viewModel.takeOverFromPlan()
        advanceUntilIdle()

        assertEquals(listOf("Jan 1–2"), state.sections.map { it.label })
        assertNull(state.message)
    }

    @Test
    fun `says when the plan has no readings left`() = runTest {
        queue.update { ReadingQueue() }
        planReadings = emptyList()
        watch()

        viewModel.takeOverFromPlan()
        advanceUntilIdle()

        assertEquals(ReadingMessage.NothingInPlan, state.message)
    }
}
