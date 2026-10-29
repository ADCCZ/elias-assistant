package io.github.adccz.elias.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import io.github.adccz.elias.domain.bible.BibleBooks
import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.reading.ReadChapter
import io.github.adccz.elias.domain.reading.ReadingLogRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One row per chapter marked as read (spec, M5). The book is stored by its ČEP abbreviation ("1 K"), the reading day
 * as its epoch day.
 */
@Entity(tableName = "read_chapters", indices = [Index(value = ["reading_day"])])
class ReadChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: String,
    val chapter: Int,
    @ColumnInfo(name = "reading_day") val readingDay: Long,
    @ColumnInfo(name = "read_at") val readAtMillis: Long,
)

@Dao
interface ReadChapterDao {
    @Query("SELECT * FROM read_chapters WHERE reading_day = :epochDay ORDER BY read_at, id")
    fun onDay(epochDay: Long): Flow<List<ReadChapterEntity>>

    @Insert
    suspend fun insert(row: ReadChapterEntity): Long

    @Query("DELETE FROM read_chapters WHERE id = :id")
    suspend fun delete(id: Long)
}

/** [ReadingLogRepository] in the Room database. */
class RoomReadingLog @Inject constructor(private val dao: ReadChapterDao) : ReadingLogRepository {
    override fun readOn(day: LocalDate): Flow<List<ReadChapter>> =
        dao.onDay(day.toEpochDay()).map { rows -> rows.mapNotNull { it.toReadChapter() } }

    override suspend fun add(chapter: Chapter, day: LocalDate, readAt: Instant): Long = dao.insert(
        ReadChapterEntity(0, chapter.book.abbreviation, chapter.number, day.toEpochDay(), readAt.toEpochMilli()),
    )

    override suspend fun remove(id: Long) = dao.delete(id)
}

/** Null for a row that no longer names a known chapter. */
internal fun ReadChapterEntity.toReadChapter(): ReadChapter? {
    val book = BibleBooks.byAbbreviation(book) ?: return null
    if (chapter !in 1..book.chapters) return null
    return ReadChapter(id, Chapter(book, chapter), LocalDate.ofEpochDay(readingDay), Instant.ofEpochMilli(readAtMillis))
}
