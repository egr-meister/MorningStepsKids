package com.morningsteps.kids.data.repository

import com.morningsteps.kids.data.local.HistoryDao
import com.morningsteps.kids.data.local.RoutineDao
import com.morningsteps.kids.data.local.RoutineEntity
import com.morningsteps.kids.data.local.RoutineHistoryEntity
import com.morningsteps.kids.data.local.RoutineHistoryStepEntity
import com.morningsteps.kids.data.local.RoutineSessionEntity
import com.morningsteps.kids.data.local.SessionDao
import com.morningsteps.kids.data.local.SessionStepStateEntity
import com.morningsteps.kids.data.local.StepEntity
import com.morningsteps.kids.data.local.TimerStateEntity
import com.morningsteps.kids.data.local.TransactionRunner
import com.morningsteps.kids.domain.routines.DraftError
import com.morningsteps.kids.domain.routines.DraftValidator
import com.morningsteps.kids.domain.routines.HistoryEntry
import com.morningsteps.kids.domain.routines.HistoryStep
import com.morningsteps.kids.domain.routines.Limits
import com.morningsteps.kids.domain.routines.Progress
import com.morningsteps.kids.domain.routines.Routine
import com.morningsteps.kids.domain.routines.RoutineDetail
import com.morningsteps.kids.domain.routines.RoutineDraft
import com.morningsteps.kids.domain.routines.RoutineNames
import com.morningsteps.kids.domain.routines.RoutineTheme
import com.morningsteps.kids.domain.routines.Session
import com.morningsteps.kids.domain.routines.Step
import com.morningsteps.kids.domain.routines.StepDraft
import com.morningsteps.kids.domain.routines.TimerState
import com.morningsteps.kids.domain.routines.TimerStatus
import com.morningsteps.kids.domain.timers.AppClock
import com.morningsteps.kids.domain.timers.TimerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

sealed interface FinishResult {
    data class Finished(val historyId: Long) : FinishResult
    data object AlreadyFinished : FinishResult
    data object NotComplete : FinishResult
    data object NotFound : FinishResult
}

data class RunningTimerInfo(val routineId: String, val routineName: String, val stepId: String, val stepLabel: String)

sealed interface StartTimerResult {
    data object Started : StartTimerResult
    data object NoTimer : StartTimerResult
    data object StepCompleted : StartTimerResult
    data class OtherRunning(val info: RunningTimerInfo) : StartTimerResult
}

sealed interface SaveResult {
    data class Saved(val routineId: String) : SaveResult
    data class Invalid(val errors: List<DraftError>) : SaveResult
    data object NotFound : SaveResult
}

data class RestoreResult(val added: Int, val skipped: Int)

/**
 * Single source of truth for routines, sessions, timers and history.
 * Every multi-row change runs inside [tx] so it is atomic.
 */
class RoutineRepository(
    private val tx: TransactionRunner,
    private val routineDao: RoutineDao,
    private val sessionDao: SessionDao,
    private val historyDao: HistoryDao,
    private val clock: AppClock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    // ---------------------------------------------------------------- reading

    val routines: Flow<List<RoutineDetail>> = combine(
        routineDao.observeRoutines(),
        routineDao.observeAllSteps(),
        sessionDao.observeSessions(),
        sessionDao.observeStepStates(),
        sessionDao.observeTimers(),
    ) { routines, steps, sessions, states, timers ->
        val stepsByRoutine = steps.groupBy { it.routineId }
        val sessionByRoutine = sessions.associateBy { it.routineId }
        val statesBySession = states.groupBy { it.sessionId }
        val timersBySession = timers.groupBy { it.sessionId }
        routines.map { r ->
            val s = sessionByRoutine[r.id]
            RoutineDetail(
                routine = r.toDomain(),
                steps = stepsByRoutine[r.id].orEmpty().sortedBy { it.displayOrder }.map { it.toDomain() },
                session = s?.let { session ->
                    Session(
                        id = session.id,
                        routineId = session.routineId,
                        startedAt = session.startedAt,
                        updatedAt = session.updatedAt,
                        completedStepIds = statesBySession[session.id].orEmpty()
                            .filter { it.completed }.map { it.stepId }.toSet(),
                        timers = timersBySession[session.id].orEmpty().associate { it.stepId to it.toDomain() },
                    )
                },
            )
        }
    }

    fun routine(routineId: String): Flow<RoutineDetail?> =
        routines.map { list -> list.firstOrNull { it.routine.id == routineId } }.distinctUntilChanged()

    val history: Flow<List<HistoryEntry>> =
        historyDao.observeHistory(Limits.MAX_HISTORY).map { list -> list.map { it.toDomain() } }

    fun historyEntry(id: Long): Flow<HistoryEntry?> = historyDao.observeEntry(id).map { it?.toDomain() }

    fun historySteps(id: Long): Flow<List<HistoryStep>> =
        historyDao.observeSteps(id).map { list ->
            list.map { HistoryStep(it.labelSnapshot, it.instructionSnapshot, it.iconKeySnapshot, it.displayOrder) }
        }

    // ---------------------------------------------------------------- sessions

    private suspend fun ensureSession(routineId: String): RoutineSessionEntity {
        sessionDao.getSessionForRoutine(routineId)?.let { return it }
        val now = clock.wallClockMillis()
        val session = RoutineSessionEntity(id = newId(), routineId = routineId, startedAt = now, updatedAt = now)
        sessionDao.insertSession(session)
        return session
    }

    private suspend fun stepOf(routineId: String, stepId: String): StepEntity? =
        routineDao.getSteps(routineId).firstOrNull { it.id == stepId }

    /** Marks a step done (persisted immediately) and stops its timer if one was configured. */
    suspend fun completeStep(routineId: String, stepId: String) = tx.run {
        stepOf(routineId, stepId) ?: return@run
        val session = ensureSession(routineId)
        val now = clock.wallClockMillis()
        sessionDao.upsertStepState(SessionStepStateEntity(session.id, stepId, completed = true, completedAt = now))
        sessionDao.deleteTimer(session.id, stepId)
        sessionDao.touchSession(session.id, now)
    }

    /** Returns the step to incomplete without touching other steps. */
    suspend fun undoStep(routineId: String, stepId: String) = tx.run {
        val session = sessionDao.getSessionForRoutine(routineId) ?: return@run
        val now = clock.wallClockMillis()
        sessionDao.upsertStepState(SessionStepStateEntity(session.id, stepId, completed = false, completedAt = null))
        sessionDao.touchSession(session.id, now)
    }

    /** Clears completion states and timers of this routine (the caller confirms first). */
    suspend fun startFresh(routineId: String) = tx.run {
        sessionDao.deleteSessionForRoutine(routineId)
    }

    /**
     * Atomically snapshots the finished routine into history and closes the session.
     * A second call finds no session and returns [FinishResult.AlreadyFinished], so duplicate taps
     * can never create two entries.
     */
    suspend fun finishRoutine(routineId: String): FinishResult = tx.run {
        val routine = routineDao.getRoutine(routineId) ?: return@run FinishResult.NotFound
        val session = sessionDao.getSessionForRoutine(routineId) ?: return@run FinishResult.AlreadyFinished
        val steps = routineDao.getSteps(routineId)
        val completed = sessionDao.getStepStates(session.id).filter { it.completed }.map { it.stepId }.toSet()
        if (!Progress.allComplete(steps.map { it.toDomain() }, completed)) return@run FinishResult.NotComplete

        val historyId = historyDao.insertEntry(
            RoutineHistoryEntity(
                routineNameSnapshot = routine.name,
                themeKeySnapshot = routine.themeKey,
                completedAt = clock.wallClockMillis(),
                totalSteps = steps.size,
            ),
        )
        historyDao.insertSteps(
            steps.sortedBy { it.displayOrder }.mapIndexed { index, s ->
                RoutineHistoryStepEntity(
                    historyId = historyId,
                    labelSnapshot = s.label,
                    instructionSnapshot = s.instruction,
                    iconKeySnapshot = s.iconKey,
                    displayOrder = index,
                )
            },
        )
        sessionDao.deleteSession(session.id)
        historyDao.trimTo(Limits.MAX_HISTORY)
        FinishResult.Finished(historyId)
    }

    // ---------------------------------------------------------------- timers

    private suspend fun runningTimersExcept(sessionId: String?, stepId: String): List<TimerStateEntity> =
        sessionDao.getTimers().filter { t ->
            t.status == TimerStatus.RUNNING.name && !(t.sessionId == sessionId && t.stepId == stepId)
        }

    /**
     * Starts or resumes the timer of a step. Only one timer may run at a time: if another one is
     * running, nothing changes unless [pauseOthers] is true.
     */
    suspend fun startTimer(routineId: String, stepId: String, pauseOthers: Boolean): StartTimerResult = tx.run {
        val step = stepOf(routineId, stepId) ?: return@run StartTimerResult.NoTimer
        val seconds = step.timerDurationSeconds ?: return@run StartTimerResult.NoTimer
        val existingSession = sessionDao.getSessionForRoutine(routineId)
        if (existingSession != null &&
            sessionDao.getStepStates(existingSession.id).any { it.stepId == stepId && it.completed }
        ) {
            return@run StartTimerResult.StepCompleted
        }

        val others = runningTimersExcept(existingSession?.id, stepId)
        // Timers that already ran out are simply finished, they do not block a new one.
        val (stillRunning, expired) = others.partition { TimerEngine.remainingMillis(it.toDomain(), clock) > 0L }
        expired.forEach { sessionDao.upsertTimer(TimerEngine.finish(it.toDomain(), clock).toEntity()) }
        if (stillRunning.isNotEmpty() && !pauseOthers) {
            val other = stillRunning.first()
            val otherSession = sessionDao.getSession(other.sessionId)
            val otherRoutine = otherSession?.let { routineDao.getRoutine(it.routineId) }
            val otherStep = otherSession?.let { stepOf(it.routineId, other.stepId) }
            return@run StartTimerResult.OtherRunning(
                RunningTimerInfo(
                    routineId = otherRoutine?.id ?: "",
                    routineName = otherRoutine?.name ?: "",
                    stepId = other.stepId,
                    stepLabel = otherStep?.label ?: "",
                ),
            )
        }
        stillRunning.forEach { sessionDao.upsertTimer(TimerEngine.pause(it.toDomain(), clock).toEntity()) }

        val session = ensureSession(routineId)
        val existing = sessionDao.getTimer(session.id, stepId)?.toDomain()
        val started = TimerEngine.start(session.id, stepId, seconds * 1000L, existing, clock)
        sessionDao.upsertTimer(started.toEntity())
        sessionDao.touchSession(session.id, clock.wallClockMillis())
        StartTimerResult.Started
    }

    suspend fun pauseTimer(routineId: String, stepId: String) = tx.run {
        val session = sessionDao.getSessionForRoutine(routineId) ?: return@run
        val timer = sessionDao.getTimer(session.id, stepId)?.toDomain() ?: return@run
        sessionDao.upsertTimer(TimerEngine.pause(timer, clock).toEntity())
    }

    /** Resetting never changes step completion. */
    suspend fun resetTimer(routineId: String, stepId: String) = tx.run {
        val step = stepOf(routineId, stepId) ?: return@run
        val seconds = step.timerDurationSeconds ?: return@run
        val session = sessionDao.getSessionForRoutine(routineId) ?: return@run
        val timer = sessionDao.getTimer(session.id, stepId)?.toDomain() ?: return@run
        sessionDao.upsertTimer(TimerEngine.reset(timer, seconds * 1000L, clock).toEntity())
    }

    /** Persists a live expiry observed by the visible step screen. Never completes the step. */
    suspend fun markTimerFinished(routineId: String, stepId: String) = tx.run {
        val session = sessionDao.getSessionForRoutine(routineId) ?: return@run
        val timer = sessionDao.getTimer(session.id, stepId)?.toDomain() ?: return@run
        if (timer.status == TimerStatus.RUNNING && TimerEngine.remainingMillis(timer, clock) <= 0L) {
            sessionDao.upsertTimer(TimerEngine.finish(timer, clock).toEntity())
        }
    }

    /** Applies background / restart recovery to every stored running timer. Silent by design. */
    suspend fun recoverTimers() = tx.run {
        sessionDao.getTimers().forEach { entity ->
            TimerEngine.recover(entity.toDomain(), clock)?.let { sessionDao.upsertTimer(it.toEntity()) }
        }
    }

    // ---------------------------------------------------------------- editing

    suspend fun loadDraft(routineId: String): RoutineDraft? = tx.run {
        val routine = routineDao.getRoutine(routineId) ?: return@run null
        RoutineDraft(
            id = routine.id,
            name = routine.name,
            theme = RoutineTheme.fromKey(routine.themeKey),
            steps = routineDao.getSteps(routineId).map {
                StepDraft(it.id, it.label, it.instruction.orEmpty(), it.iconKey, it.timerDurationSeconds)
            },
        )
    }

    suspend fun hasActiveSession(routineId: String): Boolean = sessionDao.getSessionForRoutine(routineId) != null

    suspend fun routineCount(): Int = routineDao.getRoutines().size

    /**
     * Validates and saves a draft. Saving an existing routine restarts its unfinished session
     * (the editor asks for confirmation first). History is never modified.
     */
    suspend fun saveDraft(draft: RoutineDraft): SaveResult = tx.run {
        val all = routineDao.getRoutines()
        val others = all.filter { it.id != draft.id }
        val errors = DraftValidator.validate(draft, others.map { it.name }, others.size)
        if (errors.isNotEmpty()) return@run SaveResult.Invalid(errors)
        val now = clock.wallClockMillis()
        val routineId: String
        if (draft.id == null) {
            routineId = newId()
            routineDao.insertRoutine(
                RoutineEntity(
                    id = routineId,
                    name = draft.name.trim(),
                    themeKey = draft.theme.key,
                    displayOrder = (all.maxOfOrNull { it.displayOrder } ?: -1) + 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            val existing = all.firstOrNull { it.id == draft.id } ?: return@run SaveResult.NotFound
            routineId = existing.id
            routineDao.updateRoutine(existing.copy(name = draft.name.trim(), themeKey = draft.theme.key, updatedAt = now))
            sessionDao.deleteSessionForRoutine(routineId)
            val keep = draft.steps.map { it.id }.toSet()
            val removed = routineDao.getSteps(routineId).map { it.id }.filter { it !in keep }
            if (removed.isNotEmpty()) routineDao.deleteSteps(removed)
        }
        routineDao.upsertSteps(
            draft.steps.mapIndexed { index, s ->
                StepEntity(
                    id = s.id,
                    routineId = routineId,
                    label = s.label.trim(),
                    instruction = s.instruction.trim().ifEmpty { null },
                    iconKey = s.iconKey,
                    displayOrder = index,
                    timerDurationSeconds = s.timerSeconds,
                )
            },
        )
        SaveResult.Saved(routineId)
    }

    /** Copies a routine (without progress). Returns the new id, or null when the limit is reached. */
    suspend fun duplicateRoutine(routineId: String): String? = tx.run {
        val all = routineDao.getRoutines()
        if (all.size >= Limits.MAX_ROUTINES) return@run null
        val source = all.firstOrNull { it.id == routineId } ?: return@run null
        val now = clock.wallClockMillis()
        val copyId = newId()
        routineDao.insertRoutine(
            source.copy(
                id = copyId,
                name = RoutineNames.unique(source.name.take(Limits.MAX_ROUTINE_NAME - 5).trimEnd() + " copy", all.map { it.name }),
                displayOrder = all.maxOf { it.displayOrder } + 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        routineDao.upsertSteps(routineDao.getSteps(routineId).map { it.copy(id = newId(), routineId = copyId) })
        copyId
    }

    /** Deletes the routine and its unfinished session. Finished history snapshots stay untouched. */
    suspend fun deleteRoutine(routineId: String) = tx.run {
        sessionDao.deleteSessionForRoutine(routineId)
        routineDao.deleteRoutine(routineId)
    }

    suspend fun moveRoutine(routineId: String, up: Boolean) = tx.run {
        val all = routineDao.getRoutines()
        val index = all.indexOfFirst { it.id == routineId }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in all.indices) return@run
        val reordered = all.toMutableList().apply { add(target, removeAt(index)) }
        reordered.forEachIndexed { i, r -> if (r.displayOrder != i) routineDao.updateRoutine(r.copy(displayOrder = i)) }
    }

    /** Adds the starter routines again, keeping all existing data and generating unique names. */
    suspend fun restoreStarters(): RestoreResult = tx.run {
        val all = routineDao.getRoutines()
        val rows = StarterSeed.build(
            existingNames = all.map { it.name },
            firstDisplayOrder = (all.maxOfOrNull { it.displayOrder } ?: -1) + 1,
            now = clock.wallClockMillis(),
            newId = newId,
            maxRoutines = (Limits.MAX_ROUTINES - all.size).coerceAtLeast(0),
        )
        rows.routines.forEach { routineDao.insertRoutine(it) }
        routineDao.upsertSteps(rows.steps)
        RestoreResult(added = rows.routines.size, skipped = rows.skipped)
    }

    suspend fun clearHistory() = tx.run { historyDao.deleteAll() }

    /** Wipes everything and returns to the starter routines with empty history. */
    suspend fun clearAllData() = tx.run {
        historyDao.deleteAll()
        sessionDao.deleteAllSessions()
        routineDao.deleteAllRoutines()
        val rows = StarterSeed.build(emptyList(), 0, clock.wallClockMillis(), newId, Limits.MAX_ROUTINES)
        rows.routines.forEach { routineDao.insertRoutine(it) }
        routineDao.upsertSteps(rows.steps)
    }
}

// -------------------------------------------------------------------- mapping

internal fun RoutineEntity.toDomain() = Routine(id, name, RoutineTheme.fromKey(themeKey), displayOrder, createdAt, updatedAt)

internal fun StepEntity.toDomain() = Step(id, routineId, label, instruction, iconKey, displayOrder, timerDurationSeconds)

internal fun TimerStateEntity.toDomain() = TimerState(
    sessionId = sessionId,
    stepId = stepId,
    status = TimerStatus.fromName(status),
    remainingAtCheckpointMillis = remainingAtCheckpointMillis,
    checkpointElapsedRealtimeMillis = checkpointElapsedRealtimeMillis,
    bootReference = bootReference,
)

internal fun TimerState.toEntity() = TimerStateEntity(
    sessionId = sessionId,
    stepId = stepId,
    status = status.name,
    remainingAtCheckpointMillis = remainingAtCheckpointMillis,
    checkpointElapsedRealtimeMillis = checkpointElapsedRealtimeMillis,
    bootReference = bootReference,
)

internal fun RoutineHistoryEntity.toDomain() = HistoryEntry(id, routineNameSnapshot, themeKeySnapshot, completedAt, totalSteps)
