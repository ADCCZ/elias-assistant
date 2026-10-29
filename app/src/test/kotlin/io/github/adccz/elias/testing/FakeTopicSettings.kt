package io.github.adccz.elias.testing

import io.github.adccz.elias.domain.topics.TopicSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Topic settings kept in memory. */
class FakeTopicSettings(ids: Set<Long> = emptySet()) : TopicSettingsRepository {
    private val state = MutableStateFlow(ids)

    override val topicCalendarIds: Flow<Set<Long>> = state

    override suspend fun setTopic(calendarId: Long, isTopic: Boolean) {
        state.update { if (isTopic) it + calendarId else it - calendarId }
    }
}
