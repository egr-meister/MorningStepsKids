package com.morningsteps.kids.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val themeKey: String,
    val displayOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class StepEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val label: String,
    val instruction: String?,
    val iconKey: String,
    val displayOrder: Int,
    val timerDurationSeconds: Int?,
)

/** At most one unfinished session per routine (unique index). */
@Entity(
    tableName = "routine_sessions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["routineId"], unique = true)],
)
data class RoutineSessionEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val startedAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "session_step_states",
    primaryKeys = ["sessionId", "stepId"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StepEntity::class,
            parentColumns = ["id"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stepId")],
)
data class SessionStepStateEntity(
    val sessionId: String,
    val stepId: String,
    val completed: Boolean,
    val completedAt: Long?,
)

@Entity(
    tableName = "timer_states",
    primaryKeys = ["sessionId", "stepId"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StepEntity::class,
            parentColumns = ["id"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stepId"), Index("status")],
)
data class TimerStateEntity(
    val sessionId: String,
    val stepId: String,
    val status: String,
    val remainingAtCheckpointMillis: Long,
    val checkpointElapsedRealtimeMillis: Long,
    val bootReference: String?,
)

/** Snapshot of a finished routine. Independent of the live routine so edits never change history. */
@Entity(tableName = "routine_history", indices = [Index("completedAt")])
data class RoutineHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineNameSnapshot: String,
    @ColumnInfo(defaultValue = "morning") val themeKeySnapshot: String,
    val completedAt: Long,
    val totalSteps: Int,
)

@Entity(
    tableName = "routine_history_steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("historyId")],
)
data class RoutineHistoryStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val historyId: Long,
    val labelSnapshot: String,
    val instructionSnapshot: String?,
    val iconKeySnapshot: String,
    val displayOrder: Int,
)
