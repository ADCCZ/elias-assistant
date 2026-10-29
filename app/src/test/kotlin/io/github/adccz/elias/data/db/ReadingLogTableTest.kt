package io.github.adccz.elias.data.db

import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.reading.ReadChapter
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReadingLogTableTest {
    private val day = LocalDate.of(2026, 10, 29)
    private val readAt = Instant.parse("2026-10-29T06:40:00Z")

    @Test
    fun `a row turns into the chapter it names`() {
        val row = ReadChapterEntity(7, "1 K", 13, day.toEpochDay(), readAt.toEpochMilli())

        val expected = ReadChapter(7, Chapter(BibleBooks.byAbbreviation("1 K")!!, 13), day, readAt)
        assertEquals(expected, row.toReadChapter())
    }

    @Test
    fun `a row with an unknown book or chapter is skipped`() {
        assertNull(ReadChapterEntity(1, "Tob", 1, day.toEpochDay(), 0).toReadChapter())
        assertNull(ReadChapterEntity(2, "Mal", 4, day.toEpochDay(), 0).toReadChapter())
    }
}
