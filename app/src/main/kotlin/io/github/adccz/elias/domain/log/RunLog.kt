package io.github.adccz.elias.domain.log

import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.briefing.BriefingSource
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.overview.TodayOverview
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** What a run did. */
enum class RunKind { MORNING_BRIEFING, EVENING_BRIEFING, PREFETCH }

/** Who started a run: the user with a tap, or a scheduled alarm. */
enum class RunTrigger { USER, SCHEDULE }

/** How one data source did in a run. */
enum class SourceOutcome { LOADED, FROM_CACHE, FAILED }

/** One source in a run. [source] is a technical name ("weather", "news:ČT24"), [failure] is set when it failed. */
data class SourceRecord(val source: String, val outcome: SourceOutcome, val failure: FailureReason? = null)

/** How a run ended. */
enum class RunOutcome {
    /** The briefing started playing. */
    PLAYED,

    /** The prefetch finished. Its sources say what was loaded. */
    PREFETCHED,

    NOTHING_TO_SAY,
    VOICE_NOT_READY,
    SYNTHESIS_FAILED,
    PLAYBACK_FAILED,
}

/**
 * One entry of the run log: when, what, which sources worked and who wrote the briefing.
 * It never holds the briefing's text or anything from the sources, only their status.
 */
data class RunRecord(
    val kind: RunKind,
    val trigger: RunTrigger,
    val startedAt: Instant,
    val finishedAt: Instant,
    val outcome: RunOutcome,
    val sources: List<SourceRecord> = emptyList(),
    /** Who wrote the briefing. Null for a prefetch or when no briefing was written. */
    val writtenBy: BriefingSource? = null,
    /** Why the AI did not write it, when the template did. */
    val aiFailure: AiFailureReason? = null,
)

/** The log of runs on the phone (Room). The status screen shows the latest, the Log screen (M8) all of them. */
interface RunLog {
    /** The newest run, updated whenever one is recorded. */
    val latest: Flow<RunRecord?>

    /** Adds [run] and forgets runs older than the retention period. */
    suspend fun record(run: RunRecord)

    /** When the last briefing that actually played started, or null when none has played yet. */
    suspend fun lastPlayedBriefingAt(): Instant?
}

/** The status of every source in this overview, in a fixed order, for the run log. */
fun TodayOverview.sourceRecords(): List<SourceRecord> = buildList {
    add(record("calendar", calendar))
    add(record("calendar-tomorrow", tomorrowCalendar))
    add(record("weather", weather))
    add(record("plan", plan))
    newsFeeds.forEach { (feed, result) -> add(record("news:${feed.name}", result)) }
}

private fun record(source: String, result: SourceResult<*>): SourceRecord = when (result) {
    is SourceResult.Success -> SourceRecord(
        source,
        if (result.fromCache) SourceOutcome.FROM_CACHE else SourceOutcome.LOADED,
    )

    is SourceResult.Failure -> SourceRecord(source, SourceOutcome.FAILED, result.reason)
}
