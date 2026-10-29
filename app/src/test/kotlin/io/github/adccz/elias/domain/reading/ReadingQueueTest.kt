package io.github.adccz.elias.domain.reading

import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.plan.DatedText
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingQueueTest {
    private val mark = BibleBooks.byAbbreviation("Mk")!!
    private val acts = BibleBooks.byAbbreviation("Sk")!!
    private val john = BibleBooks.byAbbreviation("J")!!
    private val psalms = BibleBooks.byAbbreviation("Ž")!!
    private val jude = BibleBooks.byAbbreviation("Ju")!!

    /** Marek (whole), Skutky 1–4, Jan 13. */
    private val queue = ReadingQueue()
        .add(mark)
        .add(acts, 1, 4)
        .add(john, 13, 13)

    private val ReadingQueue.labels get() = sections.map { it.label }

    @Test
    fun `labels show what is left of each section`() {
        assertEquals(listOf("Marek", "Skutky apoštolů 1–4", "Jan 13"), queue.labels)
        assertEquals("Žalm 23", ReadingSection(1, psalms, 23, 23).label)
        assertEquals("Žalmy 1–10", ReadingSection(1, psalms, 1, 10).label)
        assertEquals("Judův", ReadingSection(1, jude, 1, 1).label)
    }

    @Test
    fun `the next chapter is the first chapter left of the first section`() {
        assertEquals(Chapter(mark, 1), queue.next)
        assertNull(ReadingQueue().next)
    }

    @Test
    fun `reading moves one chapter on`() {
        val read = queue.advance().advance()

        assertEquals(Chapter(mark, 3), read.next)
        assertEquals(listOf("Marek 3–16", "Skutky apoštolů 1–4", "Jan 13"), read.labels)
    }

    @Test
    fun `a section read to its end leaves the queue`() {
        val lastOfMark = ReadingQueue(listOf(ReadingSection(1, mark, 16, 16), ReadingSection(2, acts, 1, 28)))

        val read = lastOfMark.advance()

        assertEquals(listOf("Skutky apoštolů"), read.labels)
        assertFalse(read.finished)
    }

    @Test
    fun `reading the very last chapter finishes the queue`() {
        val read = ReadingQueue().add(john, 13, 13).advance()

        assertTrue(read.sections.isEmpty())
        assertTrue(read.finished)
        assertEquals(read, read.advance())
    }

    @Test
    fun `undo puts the chapter back into its section`() {
        val read = queue.advance().advance()

        val undone = read.unread(Chapter(mark, 2))

        assertEquals(Chapter(mark, 2), undone.next)
        assertEquals(listOf("Marek 2–16", "Skutky apoštolů 1–4", "Jan 13"), undone.labels)
    }

    @Test
    fun `undo brings back a section that was read to its end`() {
        val finished = ReadingQueue().add(john, 13, 13).advance()

        val undone = finished.unread(Chapter(john, 13))

        assertEquals(listOf("Jan 13"), undone.labels)
        assertFalse(undone.finished)
    }

    @Test
    fun `adding, moving and removing other sections keeps the next chapter`() {
        val read = queue.advance().advance()

        val edited = read
            .add(psalms, 23, 23)
            .move(read.sections[2].id, -1)
            .remove(read.sections[1].id)

        assertEquals(Chapter(mark, 3), edited.next)
        assertEquals(listOf("Marek 3–16", "Jan 13", "Žalm 23"), edited.labels)
    }

    @Test
    fun `moving the first section down changes the next chapter`() {
        val moved = queue.move(queue.sections[0].id, 1)

        assertEquals(Chapter(acts, 1), moved.next)
        assertEquals(listOf("Skutky apoštolů 1–4", "Marek", "Jan 13"), moved.labels)
    }

    @Test
    fun `moving stops at the ends`() {
        assertEquals(queue, queue.move(queue.sections[0].id, -1))
        assertEquals(queue, queue.move(queue.sections[2].id, 5).move(queue.sections[2].id, 1))
        assertEquals(queue, queue.move(id = 99, offset = 1))
    }

    @Test
    fun `continuing with a chapter makes its section the first one`() {
        val continued = queue.continueWith(queue.sections[1].id, 3)

        assertEquals(Chapter(acts, 3), continued.next)
        assertEquals(listOf("Skutky apoštolů 3–4", "Marek", "Jan 13"), continued.labels)
    }

    @Test
    fun `continuing with an earlier chapter reads it again`() {
        val read = queue.advance().advance()

        assertEquals(listOf("Marek", "Skutky apoštolů 1–4", "Jan 13"), read.continueWith(read.sections[0].id, 1).labels)
    }

    @Test
    fun `adding to a finished queue starts it again`() {
        val finished = ReadingQueue().add(john, 13, 13).advance()

        val again = finished.add(acts)

        assertFalse(again.finished)
        assertEquals(Chapter(acts, 1), again.next)
    }

    @Test
    fun `takes the readings over from the plan from today on, joining chapters that follow each other`() {
        val today = LocalDate.of(2026, 10, 29)
        val readings = listOf(
            DatedText(today.minusDays(1), "Mk 2"),
            // Not in date order on purpose.
            DatedText(today.plusDays(2), "Mk 5"),
            DatedText(today, "Mk 3"),
            DatedText(today.plusDays(1), "Mk 4"),
            DatedText(today.plusDays(3), "Ž 23,1–3"),
            DatedText(today.plusDays(4), "Ž 23,4–6"),
            DatedText(today.plusDays(5), "Volno"),
            DatedText(today.plusDays(6), "J 13 a J 14"),
            DatedText(today.plusDays(7), "Mk 6"),
        )

        val fromPlan = ReadingQueue.fromPlan(readings, today)

        assertEquals(listOf("Marek 3–5", "Žalm 23", "Jan 13–14", "Marek 6"), fromPlan.labels)
        assertEquals(Chapter(mark, 3), fromPlan.next)
        assertEquals(4, fromPlan.sections.map { it.id }.toSet().size)
    }

    @Test
    fun `a plan without readings left gives an empty queue`() {
        val readings = listOf(DatedText(LocalDate.of(2026, 10, 1), "Mk 1"))

        assertTrue(ReadingQueue.fromPlan(readings, LocalDate.of(2026, 12, 1)).sections.isEmpty())
    }
}
