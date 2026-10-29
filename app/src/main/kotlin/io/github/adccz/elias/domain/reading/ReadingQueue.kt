package io.github.adccz.elias.domain.reading

import io.github.adccz.elias.domain.bible.BibleBook
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.plan.DatedText
import io.github.adccz.elias.domain.speech.BibleReference
import java.time.LocalDate

/**
 * A part of the reading queue that is still to be read: chapters [next] to [last] of [book].
 * Read chapters leave the section, so it always shows what is left: "Marek" becomes "Marek 3–16" after two chapters.
 * [id] tells sections apart on the screen, also when the same book is in the queue twice.
 */
data class ReadingSection(val id: Long, val book: BibleBook, val next: Int, val last: Int) {
    init {
        require(next in 1..last && last <= book.chapters) { "Invalid section ${book.abbreviation} $next–$last" }
    }

    val nextChapter: Chapter get() = Chapter(book, next)

    /** As written on the screen: "Marek", "Marek 3–16", "Jan 13", "Žalm 23", "Žalmy 1–10". */
    val label: String
        get() = when {
            next == 1 && last == book.chapters -> book.name
            next == last -> "${if (book.isPsalms) "Žalm" else book.name} $next"
            else -> "${book.name} $next–$last"
        }
}

/**
 * The Bible reading queue (spec, M5): the sections still to read, in order. The first section's next chapter is
 * today's reading. Every change returns a new queue, so the rules are easy to test.
 *
 * Moving, adding or removing other sections never changes the next chapter. [finished] is true after the last chapter
 * was marked as read, so the morning can say that the queue is done instead of falling back to the plan.
 */
data class ReadingQueue(val sections: List<ReadingSection> = emptyList(), val finished: Boolean = false) {
    /** The chapter to read next, null when the queue is empty. */
    val next: Chapter? get() = sections.firstOrNull()?.nextChapter

    /** "Přečteno": the next chapter is read. A finished section leaves the queue. */
    fun advance(): ReadingQueue {
        val first = sections.firstOrNull() ?: return this
        val rest = sections.drop(1)
        return if (first.next < first.last) {
            copy(sections = listOf(first.copy(next = first.next + 1)) + rest)
        } else {
            ReadingQueue(rest, finished = rest.isEmpty())
        }
    }

    /**
     * "Vrátit": [chapter] counts as unread again and is the next chapter. It goes back into the first section when it
     * comes right before it, otherwise a section with only this chapter goes to the top.
     */
    fun unread(chapter: Chapter): ReadingQueue {
        val first = sections.firstOrNull()
        val rest = sections.drop(1)
        val updated = if (first != null && first.book == chapter.book && first.next == chapter.number + 1) {
            listOf(first.copy(next = chapter.number)) + rest
        } else {
            listOf(ReadingSection(newId(), chapter.book, chapter.number, chapter.number)) + sections
        }
        return ReadingQueue(updated, finished = false)
    }

    /** Adds chapters [from] to [to] of [book] at the end. */
    fun add(book: BibleBook, from: Int = 1, to: Int = book.chapters): ReadingQueue =
        ReadingQueue(sections + ReadingSection(newId(), book, from, to), finished = false)

    fun remove(id: Long): ReadingQueue = copy(sections = sections.filterNot { it.id == id })

    /** Moves the section [offset] places down (positive) or up (negative), stopping at the ends. */
    fun move(id: Long, offset: Int): ReadingQueue {
        val index = sections.indexOfFirst { it.id == id }
        if (index < 0) return this
        val target = (index + offset).coerceIn(0, sections.lastIndex)
        val reordered = sections.toMutableList().apply { add(target, removeAt(index)) }
        return copy(sections = reordered)
    }

    /**
     * "Pokračuju kapitolou…": reading goes on with [chapter] of the section [id], which becomes the first one.
     * The chapter may also be an earlier one than the section had left (reading a part again).
     */
    fun continueWith(id: Long, chapter: Int): ReadingQueue {
        val section = sections.firstOrNull { it.id == id } ?: return this
        require(chapter in 1..section.last) { "Chapter $chapter is outside ${section.label}" }
        val moved = section.copy(next = chapter)
        return ReadingQueue(listOf(moved) + sections.filterNot { it.id == id }, finished = false)
    }

    private fun newId(): Long = (sections.maxOfOrNull { it.id } ?: 0) + 1

    companion object {
        /**
         * "Převzít z plánu": the plan's morning readings from [today] on, in the order of their dates. Chapters that
         * follow each other in one book become one section ("Mk 1", "Mk 2", "Mk 3" → "Marek 1–3"), the same chapter
         * on two days in a row is read once, and verses are left out. Readings that are not a known reference are
         * skipped.
         */
        fun fromPlan(readings: List<DatedText>, today: LocalDate): ReadingQueue {
            val chapters = readings
                .filter { it.date >= today }
                .sortedBy { it.date }
                .flatMap { BibleReference.chapters(it.text) }
            var queue = ReadingQueue()
            for (chapter in chapters) {
                val last = queue.sections.lastOrNull()
                queue = when {
                    last != null && last.book == chapter.book && last.last == chapter.number -> queue

                    last != null && last.book == chapter.book && last.last + 1 == chapter.number ->
                        queue.copy(sections = queue.sections.dropLast(1) + last.copy(last = chapter.number))

                    else -> queue.add(chapter.book, chapter.number, chapter.number)
                }
            }
            return queue
        }
    }
}
