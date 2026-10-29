package io.github.adccz.elias.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.adccz.elias.domain.journal.JournalEntry
import io.github.adccz.elias.domain.journal.JournalRepository
import io.github.adccz.elias.domain.journal.JournalText
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One journal entry per day. The day is stored as its epoch day, so entries sort by date.
 * The text never leaves this table except for "na čem se zlepšit" when the user asks the AI for a step.
 */
@Entity(tableName = "journal_entries")
class JournalEntity(
    @PrimaryKey @ColumnInfo(name = "epoch_day") val epochDay: Long,
    val thought: String,
    @ColumnInfo(name = "stuck_with_me") val stuckWithMe: String,
    @ColumnInfo(name = "to_improve") val toImprove: String,
    val step: String,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY epoch_day DESC")
    fun all(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal_entries WHERE epoch_day = :epochDay")
    fun onDay(epochDay: Long): Flow<JournalEntity?>

    @Upsert
    suspend fun upsert(entry: JournalEntity)

    @Query("DELETE FROM journal_entries WHERE epoch_day = :epochDay")
    suspend fun delete(epochDay: Long)
}

/** [JournalRepository] in the Room database. */
class RoomJournalRepository @Inject constructor(private val dao: JournalDao) : JournalRepository {
    override val entries: Flow<List<JournalEntry>> = dao.all().map { rows -> rows.map { it.toEntry() } }

    override fun entryOn(date: LocalDate): Flow<JournalEntry?> = dao.onDay(date.toEpochDay()).map { it?.toEntry() }

    override suspend fun put(entry: JournalEntry) = dao.upsert(entry.toEntity())

    override suspend fun remove(date: LocalDate) = dao.delete(date.toEpochDay())
}

internal fun JournalEntry.toEntity() = JournalEntity(
    epochDay = date.toEpochDay(),
    thought = text.thought,
    stuckWithMe = text.stuckWithMe,
    toImprove = text.toImprove,
    step = text.step,
    updatedAtMillis = updatedAt.toEpochMilli(),
)

internal fun JournalEntity.toEntry() = JournalEntry(
    date = LocalDate.ofEpochDay(epochDay),
    text = JournalText(thought, stuckWithMe, toImprove, step),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
)
