package com.morningsteps.kids.ui.step

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningsteps.kids.data.repository.AppSettings
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.data.repository.RunningTimerInfo
import com.morningsteps.kids.data.repository.SettingsRepository
import com.morningsteps.kids.data.repository.StartTimerResult
import com.morningsteps.kids.domain.routines.RoutineDetail
import com.morningsteps.kids.domain.routines.Step
import com.morningsteps.kids.domain.routines.TimerStatus
import com.morningsteps.kids.domain.timers.AppClock
import com.morningsteps.kids.domain.timers.ChimePolicy
import com.morningsteps.kids.domain.timers.TimerEngine
import com.morningsteps.kids.platform.ChimePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimerUi(
    val durationMillis: Long,
    val remainingMillis: Long,
    val status: TimerStatus,
) {
    val fractionRemaining: Float get() = if (durationMillis <= 0) 0f else (remainingMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
}

data class StepUiState(
    val loading: Boolean = true,
    val detail: RoutineDetail? = null,
    val step: Step? = null,
    val position: Int = 0,
    val total: Int = 0,
    val completed: Boolean = false,
    val timer: TimerUi? = null,
    val nextStep: Step? = null,
    val allComplete: Boolean = false,
    val otherRunning: RunningTimerInfo? = null,
    val missing: Boolean = false,
)

class StepViewModel(
    private val routineId: String,
    private val stepId: String,
    private val repository: RoutineRepository,
    settingsRepository: SettingsRepository,
    private val clock: AppClock,
    private val chime: ChimePlayer,
) : ViewModel() {

    /** null until the first database emission; then wraps the (possibly deleted) routine. */
    private val loadedDetail: StateFlow<Loaded?> =
        repository.routine(routineId).map { Loaded(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private val tick = MutableStateFlow(0L)
    private val otherRunning = MutableStateFlow<RunningTimerInfo?>(null)
    private data class Loaded(val detail: RoutineDetail?)

    init {
        viewModelScope.launch {
            settings.collect { if (it.timerSoundEnabled) chime.prepare() }
        }
    }

    val state: StateFlow<StepUiState> = combine(loadedDetail, tick, otherRunning) { loaded, _, other ->
        val d = loaded?.detail
        val step = d?.steps?.firstOrNull { it.id == stepId }
        if (d == null || step == null) {
            return@combine StepUiState(loading = loaded == null, missing = loaded != null)
        }
        val index = d.steps.indexOfFirst { it.id == stepId }
        val timer = step.timerDurationSeconds?.let { seconds ->
            val duration = seconds * 1000L
            val stored = d.session?.timers?.get(stepId)
            if (stored == null) {
                TimerUi(duration, duration, TimerStatus.IDLE)
            } else {
                TimerUi(duration, TimerEngine.remainingMillis(stored, clock), TimerEngine.effectiveStatus(stored, clock))
            }
        }
        StepUiState(
            loading = false,
            detail = d,
            step = step,
            position = index + 1,
            total = d.steps.size,
            completed = d.isCompleted(stepId),
            timer = timer,
            nextStep = d.nextSuggestedStep?.takeIf { it.id != stepId },
            allComplete = d.allComplete,
            otherRunning = other,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepUiState())

    /**
     * Runs only while the screen is visible (RESUMED). Recovers timers first, so a timer that ran
     * out in the background is shown as finished without any delayed sound, then ticks the display.
     * The chime plays only for an expiry observed live by this loop.
     */
    suspend fun visibleLoop() {
        repository.recoverTimers()
        var previousRemaining: Long? = null
        var previousTickAt: Long? = null
        while (true) {
            val now = clock.elapsedRealtimeMillis()
            val stored = loadedDetail.value?.detail?.session?.timers?.get(stepId)
            if (stored != null && stored.status == TimerStatus.RUNNING && TimerEngine.isReferenceValid(stored, clock)) {
                val remaining = TimerEngine.remainingMillis(stored, clock)
                if (remaining <= 0L) {
                    val chimeNow = ChimePolicy.shouldChime(
                        soundEnabled = settings.value.timerSoundEnabled,
                        previousRemainingMillis = previousRemaining,
                        previousTickElapsedMillis = previousTickAt,
                        nowElapsedMillis = now,
                        remainingMillis = remaining,
                    )
                    repository.markTimerFinished(routineId, stepId)
                    if (chimeNow) chime.play()
                    previousRemaining = 0L
                } else {
                    previousRemaining = remaining
                }
                previousTickAt = now
            } else {
                previousRemaining = null
                previousTickAt = null
            }
            tick.value = now
            delay(200)
        }
    }

    fun markDone() = viewModelScope.launch { repository.completeStep(routineId, stepId) }

    fun undoDone() = viewModelScope.launch { repository.undoStep(routineId, stepId) }

    fun startTimer(pauseOthers: Boolean = false) = viewModelScope.launch {
        when (val result = repository.startTimer(routineId, stepId, pauseOthers)) {
            is StartTimerResult.OtherRunning -> otherRunning.value = result.info
            else -> otherRunning.value = null
        }
    }

    fun dismissOtherRunning() {
        otherRunning.value = null
    }

    fun pauseTimer() = viewModelScope.launch { repository.pauseTimer(routineId, stepId) }

    fun resetTimer() = viewModelScope.launch { repository.resetTimer(routineId, stepId) }
}
