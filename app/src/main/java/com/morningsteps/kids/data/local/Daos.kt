package com.morningsteps.kids.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY displayOrder, createdAt")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM steps ORDER BY routineId, displayOrder")
    fun observeAllSteps(): Flow<List<StepEntity>>

    @Query("SELECT * FROM routines ORDER BY displayOrder, createdAt")
    suspend fun getRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM steps WHERE routineId = :routineId ORDER BY displayOrder")
    suspend fun getSteps(routineId: String): List<StepEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Upsert
    suspend fun upsertSteps(steps: List<StepEntity>)

    @Query("DELETE FROM steps WHERE id IN (:ids)")
    suspend fun deleteSteps(ids: List<String>)

    @Query("DELETE FROM routines")
    suspend fun deleteAllRoutines()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM routine_sessions")
    fun observeSessions(): Flow<List<RoutineSessionEntity>>

    @Query("SELECT * FROM session_step_states")
    fun observeStepStates(): Flow<List<SessionStepStateEntity>>

    @Query("SELECT * FROM timer_states")
    fun observeTimers(): Flow<List<TimerStateEntity>>

    @Query("SELECT * FROM routine_sessions WHERE routineId = :routineId")
    suspend fun getSessionForRoutine(routineId: String): RoutineSessionEntity?

    @Query("SELECT * FROM routine_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): RoutineSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: RoutineSessionEntity)

    @Query("UPDATE routine_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long)

    @Query("DELETE FROM routine_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM routine_sessions WHERE routineId = :routineId")
    suspend fun deleteSessionForRoutine(routineId: String)

    @Query("SELECT * FROM session_step_states WHERE sessionId = :sessionId")
    suspend fun getStepStates(sessionId: String): List<SessionStepStateEntity>

    @Upsert
    suspend fun upsertStepState(state: SessionStepStateEntity)

    @Query("SELECT * FROM timer_states WHERE sessionId = :sessionId AND stepId = :stepId")
    suspend fun getTimer(sessionId: String, stepId: String): TimerStateEntity?

    @Query("SELECT * FROM timer_states")
    suspend fun getTimers(): List<TimerStateEntity>

    @Upsert
    suspend fun upsertTimer(timer: TimerStateEntity)

    @Query("DELETE FROM timer_states WHERE sessionId = :sessionId AND stepId = :stepId")
    suspend fun deleteTimer(sessionId: String, stepId: String)

    @Query("DELETE FROM routine_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM routine_history ORDER BY completedAt DESC, id DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<RoutineHistoryEntity>>

    @Query("SELECT * FROM routine_history WHERE id = :id")
    fun observeEntry(id: Long): Flow<RoutineHistoryEntity?>

    @Query("SELECT * FROM routine_history_steps WHERE historyId = :historyId ORDER BY displayOrder")
    fun observeSteps(historyId: Long): Flow<List<RoutineHistoryStepEntity>>

    @Insert
    suspend fun insertEntry(entry: RoutineHistoryEntity): Long

    @Insert
    suspend fun insertSteps(steps: List<RoutineHistoryStepEntity>)

    /** Keeps only the newest [keep] finished routines. */
    @Query(
        "DELETE FROM routine_history WHERE id NOT IN " +
            "(SELECT id FROM routine_history ORDER BY completedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM routine_history")
    suspend fun deleteAll()
}
