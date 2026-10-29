package io.github.adccz.elias.testing

import io.github.adccz.elias.domain.journal.JournalEntry
import io.github.adccz.elias.domain.journal.JournalRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Journal entries kept in memory, newest day first like the Room table. */
class InMemoryJournal(vararg initial: JournalEntry) : JournalRepository {
    private val byDay = MutableStateFlow(initial.associateBy { it.date })

    override val entries: Flow<List<JournalEntry>> = byDay.map { it.values.sortedByDescending(JournalEntry::date) }

    override fun entryOn(date: LocalDate): Flow<JournalEntry?> = byDay.map { it[date] }

    override suspend fun put(entry: JournalEntry) = byDay.update { it + (entry.date to entry) }

    override suspend fun remove(date: LocalDate) = byDay.update { it - date }

    /** The stored entry of [date], for assertions. */
    fun stored(date: LocalDate): JournalEntry? = byDay.value[date]
}
