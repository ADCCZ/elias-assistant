package io.github.adccz.elias.domain.reading

import io.github.adccz.elias.core.time.PRAGUE_ZONE
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.plan.DatedText
import io.github.adccz.elias.testing.FixedPlans
import io.github.adccz.elias.testing.InMemoryReadingLog
import io.github.adccz.elias.testing.InMemoryReadingQueue
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BibleReadingTest {
    private val markBook = BibleBooks.byAbbreviation("Mk")!!
    private val thursday = LocalDate.of(2026, 10, 29)

    private val queue = InMemoryReadingQueue(ReadingQueue().add(markBook, 3, 16))
    private val log = InMemoryReadingLog()

    private fun clockAt(text: String): Clock =
        Clock.fixed(LocalDateTime.parse(text).atZone(PRAGUE_ZONE).toInstant(), PRAGUE_ZONE)

    private fun reading(now: String = "2026-10-29T07:00", planReadings: List<DatedText>? = null) =
        BibleReading(queue, log, FixedPlans(planReadings), clockAt(now))

    @Test
    fun `marking as read moves the queue on and records the chapter with its day`() = runTest {
        val mark = reading().markRead()

        assertEquals(Chapter(markBook, 3), mark?.chapter)
        assertEquals(Chapter(markBook, 4), queue.current.next)
        assertEquals(listOf(thursday), log.all.map { it.day })
        assertEquals(mark?.logId, log.all.single().id)
    }

    @Test
    fun `reading after midnight counts for the day before`() = runTest {
        reading(now = "2026-10-30T01:30").markRead()

        assertEquals(listOf(thursday), log.all.map { it.day })
        assertTrue(reading(now = "2026-10-30T03:59").status().readToday)
        assertFalse(reading(now = "2026-10-30T04:00").status().readToday)
    }

    @Test
    fun `undo takes back exactly one mark`() = runTest {
        val reading = reading()
        reading.markRead()
        val second = reading.markRead()!!

        reading.undo(second)

        assertEquals(Chapter(markBook, 4), queue.current.next)
        assertEquals(1, log.all.size)
    }

    @Test
    fun `an empty queue marks nothing`() = runTest {
        val empty = BibleReading(InMemoryReadingQueue(), log, FixedPlans(null), clockAt("2026-10-29T07:00"))

        assertNull(empty.markRead())
        assertTrue(log.all.isEmpty())
    }

    @Test
    fun `a broken log still moves the queue on`() = runTest {
        log.failing = true

        val mark = reading().markRead()

        assertNull(mark?.logId)
        assertEquals(Chapter(markBook, 4), queue.current.next)
    }

    @Test
    fun `the status says what the briefings need`() = runTest {
        val reading = reading()
        assertEquals(ReadingStatus(Chapter(markBook, 3), readToday = false, finished = false), reading.status())

        reading.markRead()

        assertEquals(ReadingStatus(Chapter(markBook, 4), readToday = true, finished = false), reading.status())
    }

    @Test
    fun `a finished queue is not the same as an unused one`() = runTest {
        val unused = ReadingStatus(next = null, readToday = false, finished = false)
        val finished = ReadingStatus(next = null, readToday = false, finished = true)

        assertTrue(unused.usesPlan)
        assertFalse(finished.usesPlan)
    }

    @Test
    fun `takes the plan's readings over into an empty queue`() = runTest {
        queue.update { ReadingQueue() }
        val readings = listOf(DatedText(thursday.minusDays(1), "Mk 1"), DatedText(thursday, "Mk 2"))

        assertTrue(reading(planReadings = readings).takeOverFromPlan())

        assertEquals(listOf("Marek 2"), queue.current.sections.map { it.label })
    }

    @Test
    fun `never replaces a queue that is in use`() = runTest {
        val before = queue.current

        assertFalse(reading(planReadings = listOf(DatedText(thursday, "J 1"))).takeOverFromPlan())

        assertEquals(before, queue.current)
    }

    @Test
    fun `without readings left in the plan nothing is taken over`() = runTest {
        queue.update { ReadingQueue() }

        assertFalse(reading(planReadings = listOf(DatedText(thursday.minusDays(3), "Mk 1"))).takeOverFromPlan())
        assertFalse(reading(planReadings = null).takeOverFromPlan())
    }
}
