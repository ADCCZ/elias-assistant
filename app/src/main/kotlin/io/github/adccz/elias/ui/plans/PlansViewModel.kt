package io.github.adccz.elias.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.topics.DayAgenda
import io.github.adccz.elias.domain.topics.TopicSettingsRepository
import io.github.adccz.elias.domain.topics.Topics
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One calendar in the topic picker. */
data class CalendarChoice(val calendar: PhoneCalendar, val isTopic: Boolean)

/**
 * The next days by topic. [problem] is set when the calendar could not be read (most often: no permission).
 * [topicColors] maps a topic's calendar id to its color in the calendar app.
 */
data class PlansUiState(
    val isLoading: Boolean = true,
    val problem: FailureReason? = null,
    val today: LocalDate? = null,
    val days: List<DayAgenda> = emptyList(),
    val calendars: List<CalendarChoice> = emptyList(),
    val topicColors: Map<Long, Int> = emptyMap(),
) {
    val hasTopics: Boolean get() = calendars.any { it.isTopic }
}

/**
 * Shows the plans of the next [DAYS] days from Google Calendar, grouped by topic, and lets the user mark which
 * calendars are topics. Elias only reads the calendar: new plans are added in the calendar app.
 */
@HiltViewModel
class PlansViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val topicSettings: TopicSettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private data class Loaded(
        val today: LocalDate,
        val calendars: SourceResult<List<PhoneCalendar>>,
        val events: SourceResult<Map<LocalDate, List<CalendarEvent>>>,
    )

    private val loaded = MutableStateFlow<Loaded?>(null)

    val uiState: StateFlow<PlansUiState> = combine(topicSettings.topicCalendarIds, loaded) { ids, loaded ->
        loaded?.let { stateOf(it, ids) } ?: PlansUiState()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlansUiState())

    init {
        refresh()
    }

    /** Reads the calendar again, after the permission was granted or the user came back from the calendar app. */
    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            loaded.value = Loaded(today, calendar.calendars(), calendar.eventsFrom(today, DAYS))
        }
    }

    fun setTopic(calendarId: Long, isTopic: Boolean) {
        viewModelScope.launch { topicSettings.setTopic(calendarId, isTopic) }
    }

    private fun stateOf(loaded: Loaded, topicIds: Set<Long>): PlansUiState {
        val calendars = (loaded.calendars as? SourceResult.Success)?.data.orEmpty()
        val topics = Topics.resolve(calendars, topicIds)
        return when (val events = loaded.events) {
            is SourceResult.Failure -> PlansUiState(isLoading = false, problem = events.reason, today = loaded.today)

            is SourceResult.Success -> PlansUiState(
                isLoading = false,
                today = loaded.today,
                days = events.data.map { (date, dayEvents) -> Topics.agenda(date, dayEvents, topics) },
                calendars = calendars.map { CalendarChoice(it, it.id in topicIds) },
                topicColors = calendars.filter { it.id in topicIds }.mapNotNull { c -> c.color?.let { c.id to it } }
                    .toMap(),
            )
        }
    }

    companion object {
        /** A week ahead, today included. */
        const val DAYS = 7

        /** Keeps the state through a screen rotation without reading the calendar again. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
