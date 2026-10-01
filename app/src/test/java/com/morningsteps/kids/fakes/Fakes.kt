package com.morningsteps.kids.fakes

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
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.data.repository.StarterSeed
import com.morningsteps.kids.domain.timers.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Controllable clock: monotonic time, wall time and boot identity move independently. */
class FakeClock(
    var elapsed: Long = 1_000_000L,
    var wall: Long = 1_700_000_000_000L,
    var boot: String? = "1",
) : AppClock {
    override fun elapsedRealtimeMillis() = elapsed
    override fun wallClockMillis() = wall
    override fun bootReference() = boot

    fun advance(millis: Long) {
        elapsed += millis
        wall += millis
    }

    /** Simulates a device restart: monotonic clock starts over and the boot counter changes. */
    fun reboot(newElapsed: Long = 5_000L) {
        elapsed = newElapsed
        boot = ((boot?.toIntOrNull() ?: 0) + 1).toString()
    }
}

/** In-memory database mirroring the Room schema, including ON DELETE CASCADE behaviour. */
data class FakeDbState(
    val routines: Map<String, RoutineEntity> = emptyMap(),
    val steps: Map<String, StepEntity> = emptyMap(),
    val sessions: Map<String, RoutineSessionEntity> = emptyMap(),
    val stepStates: Map<Pair<String, String>, SessionStepStateEntity> = emptyMap(),
    val timers: Map<Pair<String, String>, TimerStateEntity> = emptyMap(),
    val history: Map<Long, RoutineHistoryEntity> = emptyMap(),
    val historySteps: Map<Long, RoutineHistoryStepEntity> = emptyMap(),
    val nextHistoryId: Long = 1,
    val nextHistoryStepId: Long = 1,
)

class FakeDb {
    val state = MutableStateFlow(FakeDbState())

    fun cascadeDeleteSessions(s: FakeDbState, ids: Set<String>) = s.copy(
        sessions = s.sessions - ids,
        stepStates = s.stepStates.filterKeys { it.first !in ids },
        timers = s.timers.filterKeys { it.first !in ids },
    )

    fun cascadeDeleteSteps(s: FakeDbState, ids: Set<String>) = s.copy(
        steps = s.steps - ids,
        stepStates = s.stepStates.filterKeys { it.second !in ids },
        timers = s.timers.filterKeys { it.second !in ids },
    )

    fun cascadeDeleteRoutines(s: FakeDbState, ids: Set<String>): FakeDbState {
        val sessionIds = s.sessions.values.filter { it.routineId in ids }.map { it.id }.toSet()
        val stepIds = s.steps.values.filter { it.routineId in ids }.map { it.id }.toSet()
        val withoutSessions = cascadeDeleteSessions(s, sessionIds)
        return cascadeDeleteSteps(withoutSessions, stepIds).copy(routines = s.routines - ids)
    }
}

class FakeRoutineDao(private val db: FakeDb) : RoutineDao {
    private fun sortedRoutines(s: FakeDbState) = s.routines.values.sortedWith(compareBy({ it.displayOrder }, { it.createdAt }))

    override fun observeRoutines(): Flow<List<RoutineEntity>> = db.state.map { sortedRoutines(it) }
    override fun observeAllSteps(): Flow<List<StepEntity>> =
        db.state.map { s -> s.steps.values.sortedWith(compareBy({ it.routineId }, { it.displayOrder })) }

    override suspend fun getRoutines() = sortedRoutines(db.state.value)
    override suspend fun getRoutine(id: String) = db.state.value.routines[id]
    override suspend fun getSteps(routineId: String) =
        db.state.value.steps.values.filter { it.routineId == routineId }.sortedBy { it.displayOrder }

    override suspend fun insertRoutine(routine: RoutineEntity) {
        check(routine.id !in db.state.value.routines) { "duplicate routine id" }
        db.state.update { it.copy(routines = it.routines + (routine.id to routine)) }
    }

    override suspend fun updateRoutine(routine: RoutineEntity) {
        db.state.update { if (routine.id in it.routines) it.copy(routines = it.routines + (routine.id to routine)) else it }
    }

    override suspend fun deleteRoutine(id: String) = db.state.update { db.cascadeDeleteRoutines(it, setOf(id)) }

    override suspend fun upsertSteps(steps: List<StepEntity>) {
        db.state.update { s ->
            steps.forEach { check(it.routineId in s.routines) { "foreign key: routine ${it.routineId}" } }
            s.copy(steps = s.steps + steps.associateBy { it.id })
        }
    }

    override suspend fun deleteSteps(ids: List<String>) = db.state.update { db.cascadeDeleteSteps(it, ids.toSet()) }

    override suspend fun deleteAllRoutines() = db.state.update { db.cascadeDeleteRoutines(it, it.routines.keys) }
}

class FakeSessionDao(private val db: FakeDb) : SessionDao {
    override fun observeSessions() = db.state.map { it.sessions.values.toList() }
    override fun observeStepStates() = db.state.map { it.stepStates.values.toList() }
    override fun observeTimers() = db.state.map { it.timers.values.toList() }

    override suspend fun getSessionForRoutine(routineId: String) =
        db.state.value.sessions.values.firstOrNull { it.routineId == routineId }

    override suspend fun getSession(sessionId: String) = db.state.value.sessions[sessionId]

    override suspend fun insertSession(session: RoutineSessionEntity) {
        val s = db.state.value
        check(session.routineId in s.routines) { "foreign key" }
        check(s.sessions.values.none { it.routineId == session.routineId }) { "unique routineId" }
        db.state.update { it.copy(sessions = it.sessions + (session.id to session)) }
    }

    override suspend fun touchSession(sessionId: String, updatedAt: Long) = db.state.update { s ->
        val existing = s.sessions[sessionId] ?: return@update s
        s.copy(sessions = s.sessions + (sessionId to existing.copy(updatedAt = updatedAt)))
    }

    override suspend fun deleteSession(sessionId: String) = db.state.update { db.cascadeDeleteSessions(it, setOf(sessionId)) }

    override suspend fun deleteSessionForRoutine(routineId: String) = db.state.update { s ->
        db.cascadeDeleteSessions(s, s.sessions.values.filter { it.routineId == routineId }.map { it.id }.toSet())
    }

    override suspend fun getStepStates(sessionId: String) =
        db.state.value.stepStates.values.filter { it.sessionId == sessionId }

    override suspend fun upsertStepState(state: SessionStepStateEntity) = db.state.update { s ->
        check(state.sessionId in s.sessions && state.stepId in s.steps) { "foreign key" }
        s.copy(stepStates = s.stepStates + ((state.sessionId to state.stepId) to state))
    }

    override suspend fun getTimer(sessionId: String, stepId: String) = db.state.value.timers[sessionId to stepId]
    override suspend fun getTimers() = db.state.value.timers.values.toList()

    override suspend fun upsertTimer(timer: TimerStateEntity) = db.state.update { s ->
        check(timer.sessionId in s.sessions && timer.stepId in s.steps) { "foreign key" }
        s.copy(timers = s.timers + ((timer.sessionId to timer.stepId) to timer))
    }

    override suspend fun deleteTimer(sessionId: String, stepId: String) =
        db.state.update { it.copy(timers = it.timers - (sessionId to stepId)) }

    override suspend fun deleteAllSessions() = db.state.update { db.cascadeDeleteSessions(it, it.sessions.keys) }
}

class FakeHistoryDao(private val db: FakeDb) : HistoryDao {
    private fun ordered(s: FakeDbState) =
        s.history.values.sortedWith(compareByDescending<RoutineHistoryEntity> { it.completedAt }.thenByDescending { it.id })

    override fun observeHistory(limit: Int) = db.state.map { ordered(it).take(limit) }
    override fun observeEntry(id: Long) = db.state.map { it.history[id] }
    override fun observeSteps(historyId: Long) =
        db.state.map { s -> s.historySteps.values.filter { it.historyId == historyId }.sortedBy { it.displayOrder } }

    override suspend fun insertEntry(entry: RoutineHistoryEntity): Long {
        var id = 0L
        db.state.update { s ->
            id = s.nextHistoryId
            s.copy(history = s.history + (id to entry.copy(id = id)), nextHistoryId = id + 1)
        }
        return id
    }

    override suspend fun insertSteps(steps: List<RoutineHistoryStepEntity>) = db.state.update { s ->
        var next = s.nextHistoryStepId
        val added = steps.associate { step ->
            check(step.historyId in s.history) { "foreign key" }
            val id = next++
            id to step.copy(id = id)
        }
        s.copy(historySteps = s.historySteps + added, nextHistoryStepId = next)
    }

    override suspend fun trimTo(keep: Int) = db.state.update { s ->
        val keepIds = ordered(s).take(keep).map { it.id }.toSet()
        s.copy(
            history = s.history.filterKeys { it in keepIds },
            historySteps = s.historySteps.filterValues { it.historyId in keepIds },
        )
    }

    override suspend fun deleteAll() = db.state.update { it.copy(history = emptyMap(), historySteps = emptyMap()) }
}

/** Runs the block directly; the in-memory store has no partial-failure modes to roll back. */
object DirectTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

class TestEnv(seedStarters: Boolean = true) {
    val db = FakeDb()
    val clock = FakeClock()
    private var counter = 0
    val newId: () -> String = { "id-${++counter}" }
    val routineDao = FakeRoutineDao(db)
    val sessionDao = FakeSessionDao(db)
    val historyDao = FakeHistoryDao(db)
    val repo = RoutineRepository(DirectTransactionRunner, routineDao, sessionDao, historyDao, clock, newId)

    init {
        if (seedStarters) {
            val rows = StarterSeed.build(emptyList(), 0, clock.wall, newId, Int.MAX_VALUE)
            db.state.update { s ->
                s.copy(routines = rows.routines.associateBy { it.id }, steps = rows.steps.associateBy { it.id })
            }
        }
    }

    fun routineByName(name: String) = db.state.value.routines.values.first { it.name == name }
    fun stepsOf(routineId: String) = db.state.value.steps.values.filter { it.routineId == routineId }.sortedBy { it.displayOrder }
}
