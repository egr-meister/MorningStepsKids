package com.morningsteps.kids.domain.timers

import com.morningsteps.kids.domain.routines.TimerState
import com.morningsteps.kids.domain.routines.TimerStatus

/**
 * Clock abstraction. Active timers use only [elapsedRealtimeMillis] (monotonic, keeps counting
 * while the device sleeps, resets on reboot). [wallClockMillis] is used for timestamps only.
 */
interface AppClock {
    fun elapsedRealtimeMillis(): Long
    fun wallClockMillis(): Long

    /** Identifies the current boot; null when the platform cannot tell. */
    fun bootReference(): String?
}

/**
 * Pure timer state transitions. Nothing here decrements a counter: remaining time is always
 * `remainingAtCheckpoint - (now - checkpoint)` on the monotonic clock.
 */
object TimerEngine {

    fun idle(sessionId: String, stepId: String, durationMillis: Long, clock: AppClock) = TimerState(
        sessionId = sessionId,
        stepId = stepId,
        status = TimerStatus.IDLE,
        remainingAtCheckpointMillis = durationMillis,
        checkpointElapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
        bootReference = clock.bootReference(),
    )

    /** Starts from [existing] (resume) or from the full [durationMillis]. */
    fun start(sessionId: String, stepId: String, durationMillis: Long, existing: TimerState?, clock: AppClock): TimerState {
        val remaining = when (existing?.status) {
            TimerStatus.PAUSED, TimerStatus.PAUSED_AFTER_RESTART -> existing.remainingAtCheckpointMillis
            TimerStatus.RUNNING -> remainingMillis(existing, clock)
            else -> durationMillis
        }.let { if (it <= 0L) durationMillis else it }
        return TimerState(
            sessionId = sessionId,
            stepId = stepId,
            status = TimerStatus.RUNNING,
            remainingAtCheckpointMillis = remaining,
            checkpointElapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
            bootReference = clock.bootReference(),
        )
    }

    fun pause(state: TimerState, clock: AppClock): TimerState {
        if (state.status != TimerStatus.RUNNING) return state
        val remaining = remainingMillis(state, clock)
        return state.copy(
            status = if (remaining <= 0L) TimerStatus.FINISHED else TimerStatus.PAUSED,
            remainingAtCheckpointMillis = remaining.coerceAtLeast(0L),
            checkpointElapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
            bootReference = clock.bootReference(),
        )
    }

    fun reset(state: TimerState, durationMillis: Long, clock: AppClock): TimerState =
        idle(state.sessionId, state.stepId, durationMillis, clock)

    fun finish(state: TimerState, clock: AppClock): TimerState = state.copy(
        status = TimerStatus.FINISHED,
        remainingAtCheckpointMillis = 0L,
        checkpointElapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
        bootReference = clock.bootReference(),
    )

    /** True when the stored monotonic checkpoint can still be trusted on this boot. */
    fun isReferenceValid(state: TimerState, clock: AppClock): Boolean {
        val now = clock.elapsedRealtimeMillis()
        if (now < state.checkpointElapsedRealtimeMillis) return false
        val currentBoot = clock.bootReference()
        if (state.bootReference == null || currentBoot == null) {
            // No boot identity available: only the monotonic ordering check above can be applied.
            return state.bootReference == currentBoot
        }
        return state.bootReference == currentBoot
    }

    /** Remaining time without changing anything. Paused/idle states return their stored value. */
    fun remainingMillis(state: TimerState, clock: AppClock): Long = when (state.status) {
        TimerStatus.RUNNING -> {
            if (!isReferenceValid(state, clock)) {
                state.remainingAtCheckpointMillis
            } else {
                val elapsed = clock.elapsedRealtimeMillis() - state.checkpointElapsedRealtimeMillis
                (state.remainingAtCheckpointMillis - elapsed).coerceAtLeast(0L)
            }
        }
        TimerStatus.FINISHED -> 0L
        else -> state.remainingAtCheckpointMillis
    }

    /** Status that the UI should display right now (a running timer at zero is finished). */
    fun effectiveStatus(state: TimerState, clock: AppClock): TimerStatus = when {
        state.status == TimerStatus.RUNNING && !isReferenceValid(state, clock) -> TimerStatus.PAUSED_AFTER_RESTART
        state.status == TimerStatus.RUNNING && remainingMillis(state, clock) <= 0L -> TimerStatus.FINISHED
        else -> state.status
    }

    /**
     * Recovery when the app comes back (from background or after process death/reboot).
     * - Same boot, still time left: unchanged (time kept elapsing while away).
     * - Same boot, time ran out: FINISHED silently (no delayed sound).
     * - Rebooted / invalid reference: PAUSED_AFTER_RESTART with the last persisted remaining duration.
     * Returns null when nothing has to be persisted.
     */
    fun recover(state: TimerState, clock: AppClock): TimerState? {
        if (state.status != TimerStatus.RUNNING) return null
        if (!isReferenceValid(state, clock)) {
            return state.copy(
                status = TimerStatus.PAUSED_AFTER_RESTART,
                checkpointElapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
                bootReference = clock.bootReference(),
            )
        }
        return if (remainingMillis(state, clock) <= 0L) finish(state, clock) else null
    }
}

/**
 * Decides whether a timer that just reached zero should play the optional chime.
 * Only an expiry observed live by a continuously ticking, visible screen qualifies:
 * the previous tick must have shown time left and must be recent.
 */
object ChimePolicy {
    const val MAX_TICK_GAP_MILLIS = 1_500L

    fun shouldChime(
        soundEnabled: Boolean,
        previousRemainingMillis: Long?,
        previousTickElapsedMillis: Long?,
        nowElapsedMillis: Long,
        remainingMillis: Long,
    ): Boolean {
        if (!soundEnabled) return false
        if (remainingMillis > 0L) return false
        if (previousRemainingMillis == null || previousTickElapsedMillis == null) return false
        if (previousRemainingMillis <= 0L) return false
        val gap = nowElapsedMillis - previousTickElapsedMillis
        return gap in 0..MAX_TICK_GAP_MILLIS
    }
}
