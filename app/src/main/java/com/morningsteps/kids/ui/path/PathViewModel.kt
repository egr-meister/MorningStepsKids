package com.morningsteps.kids.ui.path

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.data.repository.SettingsRepository
import com.morningsteps.kids.domain.routines.RoutineDetail
import com.morningsteps.kids.domain.routines.TimerStatus
import com.morningsteps.kids.domain.timers.AppClock
import com.morningsteps.kids.domain.timers.TimerEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class PathUiState(
    val loading: Boolean = true,
    val detail: RoutineDetail? = null,
    val noRoutines: Boolean = false,
    /** The unfinished session began on an earlier calendar day. Progress is kept; "Start fresh" is offered. */
    val startedOnEarlierDay: Boolean = false,
    val timerStatuses: Map<String, TimerStatus> = emptyMap(),
)

class PathViewModel(
    private val repository: RoutineRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {

    val state: StateFlow<PathUiState> = combine(repository.routines, settings.settings) { routines, s ->
        val detail = routines.firstOrNull { it.routine.id == s.selectedRoutineId } ?: routines.firstOrNull()
        val zone = ZoneId.systemDefault()
        val startedDay = detail?.session?.startedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val today = Instant.ofEpochMilli(clock.wallClockMillis()).atZone(zone).toLocalDate()
        PathUiState(
            loading = false,
            detail = detail,
            noRoutines = routines.isEmpty(),
            startedOnEarlierDay = startedDay != null && startedDay.isBefore(today) && detail?.session != null,
            timerStatuses = detail?.session?.timers.orEmpty().mapValues { (_, t) -> TimerEngine.effectiveStatus(t, clock) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PathUiState())

    fun toggle(stepId: String) {
        val detail = state.value.detail ?: return
        viewModelScope.launch {
            if (detail.isCompleted(stepId)) {
                repository.undoStep(detail.routine.id, stepId)
            } else {
                repository.completeStep(detail.routine.id, stepId)
            }
        }
    }

    fun startFresh() {
        val detail = state.value.detail ?: return
        viewModelScope.launch { repository.startFresh(detail.routine.id) }
    }
}
