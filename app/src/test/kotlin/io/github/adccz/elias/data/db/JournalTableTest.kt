package io.github.adccz.elias.data.db

import io.github.adccz.elias.domain.journal.JournalEntry
import io.github.adccz.elias.domain.journal.JournalText
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JournalTableTest {
    @Test
    fun `an entry survives the trip to a row and back`() {
        val entry = JournalEntry(
            date = LocalDate.of(2026, 10, 29),
            text = JournalText("Ticho pomáhá.", "Rozhovor na chodbě", "Méně odkládat úkoly", "Přečíst deset stran"),
            updatedAt = Instant.parse("2026-10-29T21:15:00Z"),
        )

        val row = entry.toEntity()

        assertEquals(LocalDate.of(2026, 10, 29).toEpochDay(), row.epochDay)
        assertEquals(entry, row.toEntry())
    }
}
