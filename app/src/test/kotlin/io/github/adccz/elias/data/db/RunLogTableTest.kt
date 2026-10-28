package io.github.adccz.elias.data.db

import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.log.SourceOutcome
import io.github.adccz.elias.domain.log.SourceRecord
import io.github.adccz.elias.domain.model.FailureReason
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RunLogTableTest {
    private val run = RunRecord(
        kind = RunKind.EVENING_BRIEFING,
        trigger = RunTrigger.SCHEDULE,
        startedAt = Instant.parse("2026-10-28T21:00:00Z"),
        finishedAt = Instant.parse("2026-10-28T21:00:07Z"),
        outcome = RunOutcome.PLAYED,
        sources = listOf(
            SourceRecord("calendar", SourceOutcome.LOADED),
            SourceRecord("weather", SourceOutcome.FROM_CACHE),
            SourceRecord("news:ČT24", SourceOutcome.FAILED, FailureReason.TIMEOUT),
        ),
        writtenBy = BriefingSource.TEMPLATE,
        aiFailure = AiFailureReason.NO_CREDIT,
    )

    @Test
    fun `a run survives the trip into the table and back`() {
        assertEquals(run, run.toEntity().toRecord())
    }

    @Test
    fun `a prefetch without a briefing survives the trip too`() {
        val prefetch = run.copy(
            kind = RunKind.PREFETCH,
            outcome = RunOutcome.PREFETCHED,
            writtenBy = null,
            aiFailure = null,
        )

        assertEquals(prefetch, prefetch.toEntity().toRecord())
    }

    @Test
    fun `values this version does not know become safe defaults`() {
        val entity = RunEntity(
            kind = "NIGHT_BRIEFING",
            trigger = "WIDGET",
            startedAtMillis = 0,
            finishedAtMillis = 0,
            outcome = "EXPLODED",
            sources = "not json",
            writtenBy = "ROBOT",
            aiFailure = "SOLAR_FLARE",
        )

        val record = entity.toRecord()

        assertEquals(RunKind.EVENING_BRIEFING, record.kind)
        assertEquals(RunTrigger.USER, record.trigger)
        assertEquals(RunOutcome.PLAYBACK_FAILED, record.outcome)
        assertEquals(emptyList<SourceRecord>(), record.sources)
        assertEquals(BriefingSource.TEMPLATE, record.writtenBy)
        assertEquals(AiFailureReason.UNKNOWN, record.aiFailure)
    }

    @Test
    fun `recording a run forgets runs older than thirty days`() = runTest {
        val dao = FakeRunDao()

        RoomRunLog(dao).record(run)

        assertEquals(1, dao.rows.size)
        assertEquals(run.startedAt.minus(Duration.ofDays(30)).toEpochMilli(), dao.deletedBefore)
    }

    @Test
    fun `reads the start of the last played briefing`() = runTest {
        val dao = FakeRunDao().apply { lastPlayed = run.startedAt.toEpochMilli() }

        assertEquals(run.startedAt, RoomRunLog(dao).lastPlayedBriefingAt())
    }

    @Test
    fun `the latest run comes from the table`() = runTest {
        val dao = FakeRunDao()
        val log = RoomRunLog(dao)

        log.record(run)

        assertEquals(run, log.latest.first())
    }

    private class FakeRunDao : RunDao {
        val rows = mutableListOf<RunEntity>()
        var deletedBefore: Long? = null
        var lastPlayed: Long? = null
        private val newest = MutableStateFlow<RunEntity?>(null)

        override suspend fun insert(run: RunEntity) {
            rows += run
            newest.value = run
        }

        override fun latest(): Flow<RunEntity?> = newest

        override suspend fun lastPlayedBriefingStart(): Long? = lastPlayed

        override suspend fun deleteStartedBefore(cutoffMillis: Long) {
            deletedBefore = cutoffMillis
        }
    }
}
