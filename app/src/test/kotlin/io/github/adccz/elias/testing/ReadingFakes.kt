package io.github.adccz.elias.testing

import io.github.adccz.elias.domain.bible.Chapter
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.plan.DatedText
import io.github.adccz.elias.domain.plan.LoadedPlan
import io.github.adccz.elias.domain.plan.Plan
import io.github.adccz.elias.domain.plan.PlanRepository
import io.github.adccz.elias.domain.reading.ReadChapter
import io.github.adccz.elias.domain.reading.ReadingLogRepository
import io.github.adccz.elias.domain.reading.ReadingQueue
import io.github.adccz.elias.domain.reading.ReadingQueueRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** The reading queue kept in memory, like the DataStore file. */
class InMemoryReadingQueue(initial: ReadingQueue = ReadingQueue()) : ReadingQueueRepository {
    private val state = MutableStateFlow(initial)

    override val queue: Flow<ReadingQueue> = state

    override suspend fun update(change: (ReadingQueue) -> ReadingQueue): ReadingQueue = state.updateAndGet(change)

    /** The stored queue, for assertions. */
    val current: ReadingQueue get() = state.value
}

/** Read chapters kept in memory, like the Room table. [failing] makes every write throw. */
class InMemoryReadingLog(var failing: Boolean = false) : ReadingLogRepository {
    private val rows = MutableStateFlow(emptyList<ReadChapter>())
    private var nextId = 1L

    override fun readOn(day: LocalDate): Flow<List<ReadChapter>> = rows.map { all -> all.filter { it.day == day } }

    override suspend fun add(chapter: Chapter, day: LocalDate, readAt: Instant): Long {
        if (failing) error("The database is broken")
        val id = nextId++
        rows.update { it + ReadChapter(id, chapter, day, readAt) }
        return id
    }

    override suspend fun remove(id: Long) = rows.update { all -> all.filterNot { it.id == id } }

    /** Everything stored, for assertions. */
    val all: List<ReadChapter> get() = rows.value
}

/** A fictional plan with only morning readings, or no plan at all. */
class FixedPlans(private val readings: List<DatedText>?) : PlanRepository {
    override suspend fun loadPlan(): SourceResult<LoadedPlan> {
        val morningReadings = readings ?: return SourceResult.Failure(FailureReason.INVALID_DATA)
        val plan = Plan(
            name = "Vzorový plán",
            validFrom = LocalDate.of(2026, 10, 1),
            validTo = LocalDate.of(2026, 11, 30),
            phases = emptyList(),
            dailyMinimum = emptyList(),
            rules = emptyList(),
            weeklyThemes = emptyList(),
            morningReadings = morningReadings,
            saturdayStudies = emptyList(),
            recurringItems = emptyList(),
            oneTimeItems = emptyList(),
            deadlines = emptyList(),
        )
        return SourceResult.Success(LoadedPlan(plan, isSample = true), Instant.EPOCH)
    }
}
