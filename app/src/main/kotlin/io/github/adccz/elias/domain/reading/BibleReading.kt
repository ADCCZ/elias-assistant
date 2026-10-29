package io.github.adccz.elias.domain.reading

import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.journal.JournalDay
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.plan.PlanRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Where the queue is kept: DataStore. Thin on purpose, the rules live in [ReadingQueue]. */
interface ReadingQueueRepository {
    val queue: Flow<ReadingQueue>

    /** Applies [change] to the stored queue in one step. Returns the new queue, or null when it could not be saved. */
    suspend fun update(change: (ReadingQueue) -> ReadingQueue): ReadingQueue?
}

/** One chapter marked as read on the reading [day] (which starts at 4:00), at [readAt]. */
data class ReadChapter(val id: Long, val chapter: Chapter, val day: LocalDate, val readAt: Instant)

/** The chapters marked as read, with their day: the Room database. */
interface ReadingLogRepository {
    fun readOn(day: LocalDate): Flow<List<ReadChapter>>

    /** Stores a read chapter and returns its id. */
    suspend fun add(chapter: Chapter, day: LocalDate, readAt: Instant): Long

    suspend fun remove(id: Long)
}

/** What "Přečteno" did, so "Vrátit" can take exactly that back. [logId] is null when the log could not be written. */
data class ReadMark(val chapter: Chapter, val logId: Long?)

/** What the briefings say about the reading (spec, M5). */
data class ReadingStatus(
    /** The chapter to read next, null when the queue is empty. */
    val next: Chapter?,
    /** Something was marked as read today. */
    val readToday: Boolean,
    /** The queue was read to its end. */
    val finished: Boolean,
) {
    /** The queue is not used: the plan's reading by date applies (spec, M5). */
    val usesPlan: Boolean get() = next == null && !finished

    companion object {
        val EMPTY = ReadingStatus(next = null, readToday = false, finished = false)
    }
}

/**
 * The Bible reading by a queue (spec, M5): the queue, "Přečteno" and "Vrátit", the edits and taking the readings over
 * from the plan. A skipped day moves nothing, more chapters mean more taps. The reading day starts at 4:00, like the
 * journal's ([JournalDay]).
 */
class BibleReading @Inject constructor(
    private val queues: ReadingQueueRepository,
    private val log: ReadingLogRepository,
    private val plans: PlanRepository,
    private val clock: Clock,
) {
    val queue: Flow<ReadingQueue> get() = queues.queue

    /** The reading day now: before 4:00 it is still the day before. */
    fun today(): LocalDate = JournalDay.of(ZonedDateTime.now(clock))

    fun readOn(day: LocalDate): Flow<List<ReadChapter>> = log.readOn(day)

    /** "Přečteno": the next chapter is read today. Null when the queue is empty or could not be saved. */
    suspend fun markRead(): ReadMark? {
        var read: Chapter? = null
        queues.update { queue ->
            read = queue.next
            queue.advance()
        } ?: return null
        val chapter = read ?: return null
        val logId = try {
            log.add(chapter, today(), clock.instant())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The queue has moved on, which is what the user sees. Only "already read today" is lost.
            null
        }
        return ReadMark(chapter, logId)
    }

    /** "Vrátit": takes back what [mark] did. */
    suspend fun undo(mark: ReadMark) {
        mark.logId?.let { log.remove(it) }
        queues.update { it.unread(mark.chapter) }
    }

    suspend fun edit(change: (ReadingQueue) -> ReadingQueue) {
        queues.update(change)
    }

    /**
     * "Převzít z plánu": replaces an empty queue with the plan's morning readings from today on. Returns false when
     * the plan has no readings left (or cannot be loaded); a queue that is not empty is never replaced.
     */
    suspend fun takeOverFromPlan(): Boolean {
        val plan = (plans.loadPlan() as? SourceResult.Success)?.data?.plan ?: return false
        val fromPlan = ReadingQueue.fromPlan(plan.morningReadings, today())
        if (fromPlan.sections.isEmpty()) return false
        var replaced = false
        queues.update { queue ->
            replaced = queue.sections.isEmpty()
            if (replaced) fromPlan else queue
        } ?: return false
        return replaced
    }

    /** What the briefings need: the next chapter, whether today is done and whether the queue is finished. */
    suspend fun status(): ReadingStatus {
        val queue = queues.queue.first()
        val readToday = log.readOn(today()).first().isNotEmpty()
        return ReadingStatus(next = queue.next, readToday = readToday, finished = queue.finished)
    }
}
