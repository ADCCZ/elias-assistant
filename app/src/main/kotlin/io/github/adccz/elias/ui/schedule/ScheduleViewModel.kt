package io.github.adccz.elias.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.schedule.ScheduleCalculator
import io.github.adccz.elias.domain.schedule.ScheduleRepository
import io.github.adccz.elias.domain.schedule.ScheduledRun
import io.github.adccz.elias.domain.schedule.WeeklySchedule
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The weekly schedule and what it plans next, so the user sees the effect of a change at once. */
data class ScheduleUiState(
    val schedule: WeeklySchedule = WeeklySchedule.DEFAULT,
    val nextRuns: List<ScheduledRun> = emptyList(),
)

/** Which of a day's two times the user edits. */
enum class TimeSlot { MORNING, EVENING }

/**
 * Lets the user set the morning and the evening time of each day, or switch them off. Saving is enough:
 * the app plans the alarms again whenever the schedule changes (EliasApp).
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val schedules: ScheduleRepository,
    private val calculator: ScheduleCalculator,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<ScheduleUiState> = schedules.schedule
        .map { schedule -> ScheduleUiState(schedule, calculator.nextRuns(schedule, ZonedDateTime.now(clock))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ScheduleUiState())

    /** Sets [slot] of [day] to [time], or switches it off when [time] is null. */
    fun setTime(day: DayOfWeek, slot: TimeSlot, time: LocalTime?) {
        viewModelScope.launch {
            // The stored schedule, not the screen state, which may not have caught up yet.
            val current = schedules.schedule.first().on(day)
            val changed = when (slot) {
                TimeSlot.MORNING -> current.copy(morning = time)
                TimeSlot.EVENING -> current.copy(evening = time)
            }
            schedules.setDay(day, changed)
        }
    }

    private companion object {
        /** Keeps the state through a screen rotation without reading the schedule again. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
