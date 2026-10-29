package io.github.adccz.elias.domain.journal

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * What the user wrote about one day (spec, M4.5). It stays on the phone: only [toImprove] may leave it, and only when
 * the user asks the AI for a step.
 */
data class JournalText(
    /** "Myšlenka dne". */
    val thought: String = "",
    /** "Co mi utkvělo". */
    val stuckWithMe: String = "",
    /** "Na čem se zlepšit". */
    val toImprove: String = "",
    /** One concrete step for the next day, suggested by the AI or written by the user. */
    val step: String = "",
) {
    val isBlank: Boolean get() = listOf(thought, stuckWithMe, toImprove, step).all { it.isBlank() }

    fun trimmed() = JournalText(thought.trim(), stuckWithMe.trim(), toImprove.trim(), step.trim())
}

/** A saved journal entry: the [text] of [date], last changed at [updatedAt]. */
data class JournalEntry(val date: LocalDate, val text: JournalText, val updatedAt: Instant)

/** Where journal entries are kept: the Room database. Thin on purpose, the rules live in [Journal]. */
interface JournalRepository {
    /** All entries, newest day first. */
    val entries: Flow<List<JournalEntry>>

    fun entryOn(date: LocalDate): Flow<JournalEntry?>

    /** Adds the entry or replaces the one of the same day. */
    suspend fun put(entry: JournalEntry)

    suspend fun remove(date: LocalDate)
}

/** Which day an entry belongs to. */
object JournalDay {
    /** Writing before this time still belongs to the day before: the evening often ends after midnight. */
    val STARTS_AT: LocalTime = LocalTime.of(4, 0)

    fun of(now: ZonedDateTime): LocalDate =
        if (now.toLocalTime() < STARTS_AT) now.toLocalDate().minusDays(1) else now.toLocalDate()
}

/** The rules of the journal: one entry per day, an emptied entry disappears, times come from the [Clock]. */
class Journal @Inject constructor(private val repository: JournalRepository, private val clock: Clock) {
    val entries: Flow<List<JournalEntry>> get() = repository.entries

    /** The day an entry written now belongs to. */
    fun today(): LocalDate = JournalDay.of(ZonedDateTime.now(clock))

    fun entryOn(date: LocalDate): Flow<JournalEntry?> = repository.entryOn(date)

    /** Saves [text] as the entry of [date]. An entry with nothing in it is removed instead of kept empty. */
    suspend fun save(date: LocalDate, text: JournalText) {
        val clean = text.trimmed()
        if (clean.isBlank) {
            repository.remove(date)
        } else {
            repository.put(JournalEntry(date, clean, clock.instant()))
        }
    }

    /** True when the entry of [date] has something in it. The evening reminder is skipped then. */
    suspend fun isWritten(date: LocalDate): Boolean = repository.entryOn(date).first() != null

    /**
     * What the morning briefing of [morningDate] reminds of: the step from the day before, or what the user wanted to
     * improve when there is no step. Null when there is nothing to remind of.
     */
    suspend fun reminderFor(morningDate: LocalDate): JournalReminder? {
        val text = repository.entryOn(morningDate.minusDays(1)).first()?.text ?: return null
        return when {
            text.step.isNotBlank() -> JournalReminder(text.step, isStep = true)
            text.toImprove.isNotBlank() -> JournalReminder(text.toImprove, isStep = false)
            else -> null
        }
    }
}

/** Yesterday's step, or what the user wanted to improve when no step was written ([isStep] false). */
data class JournalReminder(val text: String, val isStep: Boolean)

/**
 * What the briefings say about the journal. Read on the phone and spoken from a template, never sent to the AI.
 * [writtenToday] is null when the journal could not be read: then the evening says nothing rather than nag.
 */
data class JournalStatus(val writtenToday: Boolean?, val reminder: JournalReminder?) {
    companion object {
        val UNKNOWN = JournalStatus(writtenToday = null, reminder = null)
    }
}
