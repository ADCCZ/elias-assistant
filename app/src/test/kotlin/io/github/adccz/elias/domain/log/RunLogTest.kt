package io.github.adccz.elias.domain.log

import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.testing.TestOverviews
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RunLogTest {
    @Test
    fun `records every source of the overview with its outcome`() {
        val overview = TestOverviews.rich().copy(
            weather = SourceResult.Success(TestOverviews.richWeather, Instant.EPOCH, fromCache = true),
            tomorrowCalendar = SourceResult.Failure(FailureReason.NO_PERMISSION),
        )

        val records = overview.sourceRecords()

        assertEquals(
            listOf("calendar", "calendar-tomorrow", "weather", "plan"),
            records.take(4).map { it.source },
        )
        assertEquals(SourceRecord("calendar", SourceOutcome.LOADED), records[0])
        assertEquals(SourceRecord("calendar-tomorrow", SourceOutcome.FAILED, FailureReason.NO_PERMISSION), records[1])
        assertEquals(SourceRecord("weather", SourceOutcome.FROM_CACHE), records[2])
        assertEquals(overview.newsFeeds.keys.map { "news:${it.name}" }, records.drop(4).map { it.source })
    }
}
