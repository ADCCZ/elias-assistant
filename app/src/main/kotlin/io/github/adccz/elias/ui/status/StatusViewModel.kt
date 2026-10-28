package io.github.adccz.elias.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adccz.elias.domain.log.RunLog
import io.github.adccz.elias.domain.log.RunRecord
import io.github.adccz.elias.domain.schedule.ScheduleCalculator
import io.github.adccz.elias.domain.schedule.ScheduleRepository
import io.github.adccz.elias.domain.schedule.ScheduledRun
import io.github.adccz.elias.domain.status.SystemChecks
import io.github.adccz.elias.domain.status.SystemStatus
import io.github.adccz.elias.domain.voice.VoiceChecker
import io.github.adccz.elias.domain.voice.VoiceStatus
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the status screen shows. Null means "not known yet". */
data class StatusUiState(
    val system: SystemStatus? = null,
    val voice: VoiceStatus? = null,
    val nextRuns: List<ScheduledRun> = emptyList(),
    val lastRun: RunRecord? = null,
)

/**
 * Whether Elias can work on its own (spec: the "Stav" screen): permissions, the battery exemption, the Czech voice,
 * what is planned next and how the last run went. Checked again whenever the user comes back from a fix.
 */
@HiltViewModel
class StatusViewModel @Inject constructor(
    private val checks: SystemChecks,
    private val voiceChecker: VoiceChecker,
    private val schedules: ScheduleRepository,
    private val calculator: ScheduleCalculator,
    private val runLog: RunLog,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    private var voiceJob: Job? = null

    init {
        viewModelScope.launch {
            schedules.schedule.collect { schedule ->
                _uiState.update { it.copy(nextRuns = calculator.nextRuns(schedule, ZonedDateTime.now(clock))) }
            }
        }
        viewModelScope.launch {
            runLog.latest.collect { run -> _uiState.update { it.copy(lastRun = run) } }
        }
        refresh()
    }

    /** Checks everything again, for example after the user came back from the settings. */
    fun refresh() {
        _uiState.update { it.copy(system = checks.current()) }
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            val status = voiceChecker.check()
            _uiState.update { it.copy(voice = status) }
        }
    }
}
