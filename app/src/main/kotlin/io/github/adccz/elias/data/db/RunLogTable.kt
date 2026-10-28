package io.github.adccz.elias.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunLog
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.log.RunTrigger
import io.github.adccz.elias.domain.log.SourceOutcome
import io.github.adccz.elias.domain.log.SourceRecord
import io.github.adccz.elias.domain.model.FailureReason
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One row per run. Enums are stored by name, the sources as a small JSON list. */
@Entity(tableName = "runs")
class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val trigger: String,
    @ColumnInfo(name = "started_at") val startedAtMillis: Long,
    @ColumnInfo(name = "finished_at") val finishedAtMillis: Long,
    val outcome: String,
    val sources: String,
    @ColumnInfo(name = "written_by") val writtenBy: String?,
    @ColumnInfo(name = "ai_failure") val aiFailure: String?,
)

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: RunEntity)

    @Query("SELECT * FROM runs ORDER BY started_at DESC, id DESC LIMIT 1")
    fun latest(): Flow<RunEntity?>

    @Query(
        "SELECT started_at FROM runs WHERE outcome = 'PLAYED' AND kind != 'PREFETCH' " +
            "ORDER BY started_at DESC LIMIT 1",
    )
    suspend fun lastPlayedBriefingStart(): Long?

    @Query("DELETE FROM runs WHERE started_at < :cutoffMillis")
    suspend fun deleteStartedBefore(cutoffMillis: Long)
}

/** [RunLog] in the Room database. Runs older than [RETENTION] are deleted whenever a new one is recorded. */
class RoomRunLog @Inject constructor(private val dao: RunDao) : RunLog {
    override val latest: Flow<RunRecord?> = dao.latest().map { it?.toRecord() }

    override suspend fun record(run: RunRecord) {
        dao.insert(run.toEntity())
        dao.deleteStartedBefore(run.startedAt.minus(RETENTION).toEpochMilli())
    }

    override suspend fun lastPlayedBriefingAt(): Instant? = dao.lastPlayedBriefingStart()?.let(Instant::ofEpochMilli)

    companion object {
        /** Long enough to look back at a month of mornings and evenings, short enough to stay small. */
        val RETENTION: Duration = Duration.ofDays(30)
    }
}

@Serializable
private class StoredSource(val source: String, val outcome: String, val failure: String? = null)

private val json = Json { ignoreUnknownKeys = true }

internal fun RunRecord.toEntity() = RunEntity(
    kind = kind.name,
    trigger = trigger.name,
    startedAtMillis = startedAt.toEpochMilli(),
    finishedAtMillis = finishedAt.toEpochMilli(),
    outcome = outcome.name,
    sources = json.encodeToString(sources.map { StoredSource(it.source, it.outcome.name, it.failure?.name) }),
    writtenBy = writtenBy?.name,
    aiFailure = aiFailure?.name,
)

/** Reads a row back. A value a newer or older app version wrote and this one does not know becomes a safe default. */
internal fun RunEntity.toRecord() = RunRecord(
    kind = enumOr(kind, RunKind.EVENING_BRIEFING),
    trigger = enumOr(trigger, RunTrigger.USER),
    startedAt = Instant.ofEpochMilli(startedAtMillis),
    finishedAt = Instant.ofEpochMilli(finishedAtMillis),
    outcome = enumOr(outcome, RunOutcome.PLAYBACK_FAILED),
    sources = runCatching { json.decodeFromString<List<StoredSource>>(sources) }.getOrDefault(emptyList()).map {
        SourceRecord(
            source = it.source,
            outcome = enumOr(it.outcome, SourceOutcome.FAILED),
            failure = it.failure?.let { name -> enumOr(name, FailureReason.UNKNOWN) },
        )
    },
    writtenBy = writtenBy?.let { enumOr(it, BriefingSource.TEMPLATE) },
    aiFailure = aiFailure?.let { enumOr(it, AiFailureReason.UNKNOWN) },
)

private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
