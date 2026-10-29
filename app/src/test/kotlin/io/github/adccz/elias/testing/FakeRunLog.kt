package io.github.adccz.elias.testing

import io.github.adccz.elias.domain.log.RunKind
import io.github.adccz.elias.domain.log.RunLog
import io.github.adccz.elias.domain.log.RunOutcome
import io.github.adccz.elias.domain.log.RunRecord
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

/** A [RunLog] in a list, for tests. [failing] makes recording throw, like a broken database. */
class FakeRunLog(private val failing: Boolean = false) : RunLog {
    val runs = mutableListOf<RunRecord>()
    override val latest = MutableStateFlow<RunRecord?>(null)

    override suspend fun record(run: RunRecord) {
        if (failing) throw IllegalStateException("The database is broken")
        runs += run
        latest.value = run
    }

    override suspend fun lastPlayedBriefingAt(): Instant? =
        runs.filter { it.outcome == RunOutcome.PLAYED && it.kind != RunKind.PREFETCH }.maxOfOrNull { it.startedAt }
}
