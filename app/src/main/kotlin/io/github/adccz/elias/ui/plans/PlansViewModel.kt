package io.github.adccz.elias.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.calendar.CalendarEvent
import io.github.adccz.elias.domain.calendar.CalendarRepository
import io.github.adccz.elias.domain.calendar.CalendarSettingsRepository
import io.github.adccz.elias.domain.calendar.PhoneCalendar
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.topics.DayAgenda
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

/** One calendar in the picker: whether Elias reads it at all, and whether it is a topic. */
data class CalendarChoice(val calendar: PhoneCalendar, val isTopic: Boolean, val isRead: Boolean = true)

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
    val hasTopics: Boolean get() = calendars.any { it.isTopic && it.isRead }
}

/**
 * Shows the plans of the next [DAYS] days from Google Calendar, grouped by topic, and lets the user choose which
 * calendars Elias reads and which are topics. Elias only reads the calendar: new plans are added in the calendar app.
 */
@HiltViewModel
class PlansViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val calendarSettings: CalendarSettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private data class Loaded(
        val today: LocalDate,
        val calendars: SourceResult<List<PhoneCalendar>>,
        val events: SourceResult<Map<LocalDate, List<CalendarEvent>>>,
    )

    private val loaded = MutableStateFlow<Loaded?>(null)

    val uiState: StateFlow<PlansUiState> = combine(
        calendarSettings.topicCalendarIds,
        calendarSettings.hiddenCalendarIds,
        loaded,
    ) { topicIds, hiddenIds, loaded ->
        loaded?.let { stateOf(it, topicIds, hiddenIds) } ?: PlansUiState()
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
        viewModelScope.launch { calendarSettings.setTopic(calendarId, isTopic) }
    }

    /** Switches reading [calendarId] on or off, then reads the calendar again with the new choice. */
    fun setRead(calendarId: Long, read: Boolean) {
        viewModelScope.launch {
            calendarSettings.setRead(calendarId, read)
            refresh()
        }
    }

    private fun stateOf(loaded: Loaded, topicIds: Set<Long>, hiddenIds: Set<Long>): PlansUiState {
        val calendars = (loaded.calendars as? SourceResult.Success)?.data.orEmpty()
        val topics = Topics.resolve(calendars, topicIds - hiddenIds)
        return when (val events = loaded.events) {
            is SourceResult.Failure -> PlansUiState(isLoading = false, problem = events.reason, today = loaded.today)

            is SourceResult.Success -> PlansUiState(
                isLoading = false,
                today = loaded.today,
                days = events.data.map { (date, dayEvents) -> Topics.agenda(date, dayEvents, topics) },
                calendars = calendars.map { CalendarChoice(it, it.id in topicIds, it.id !in hiddenIds) },
                topicColors = topics.mapNotNull { topic ->
                    calendars.firstOrNull { it.id == topic.calendarId }?.color?.let { topic.calendarId to it }
                }.toMap(),
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
