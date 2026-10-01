package com.morningsteps.kids.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.morningsteps.kids.data.repository.StarterSeed
import java.util.UUID

@Database(
    entities = [
        RoutineEntity::class,
        StepEntity::class,
        RoutineSessionEntity::class,
        SessionStepStateEntity::class,
        TimerStateEntity::class,
        RoutineHistoryEntity::class,
        RoutineHistoryStepEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "morningsteps.db"

        /**
         * Future schema changes must add a [androidx.room.migration.Migration] (or an AutoMigration)
         * to this list. Destructive migration is intentionally not enabled.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(SeedCallback)
                .build()
    }

    /** Seeds the starter routines exactly once: when the database file is first created. */
    private object SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val seed = StarterSeed.build(
                existingNames = emptyList(),
                firstDisplayOrder = 0,
                now = now,
                newId = { UUID.randomUUID().toString() },
                maxRoutines = Int.MAX_VALUE,
            )
            seed.routines.forEach { r ->
                db.insert(
                    "routines", SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("id", r.id)
                        put("name", r.name)
                        put("themeKey", r.themeKey)
                        put("displayOrder", r.displayOrder)
                        put("createdAt", r.createdAt)
                        put("updatedAt", r.updatedAt)
                    },
                )
            }
            seed.steps.forEach { s ->
                db.insert(
                    "steps", SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("id", s.id)
                        put("routineId", s.routineId)
                        put("label", s.label)
                        if (s.instruction == null) putNull("instruction") else put("instruction", s.instruction)
                        put("iconKey", s.iconKey)
                        put("displayOrder", s.displayOrder)
                        if (s.timerDurationSeconds == null) {
                            putNull("timerDurationSeconds")
                        } else {
                            put("timerDurationSeconds", s.timerDurationSeconds)
                        }
                    },
                )
            }
        }
    }
}

class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
