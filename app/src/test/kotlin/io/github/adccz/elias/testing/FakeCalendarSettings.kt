package io.github.adccz.elias.testing

import io.github.adccz.elias.domain.calendar.CalendarSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Calendar choices kept in memory. */
class FakeCalendarSettings(topics: Set<Long> = emptySet(), hidden: Set<Long> = emptySet()) :
    CalendarSettingsRepository {
    private val topicIds = MutableStateFlow(topics)
    private val hiddenIds = MutableStateFlow(hidden)

    override val topicCalendarIds: Flow<Set<Long>> = topicIds

    override val hiddenCalendarIds: Flow<Set<Long>> = hiddenIds

    override suspend fun setTopic(calendarId: Long, isTopic: Boolean) {
        topicIds.update { if (isTopic) it + calendarId else it - calendarId }
    }

    override suspend fun setRead(calendarId: Long, read: Boolean) {
        hiddenIds.update { if (read) it - calendarId else it + calendarId }
    }
}
