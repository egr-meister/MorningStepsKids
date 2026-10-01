package com.morningsteps.kids

import android.content.Context
import com.morningsteps.kids.data.local.AppDatabase
import com.morningsteps.kids.data.local.RoomTransactionRunner
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.data.repository.SettingsRepository
import com.morningsteps.kids.domain.timers.AppClock
import com.morningsteps.kids.platform.AndroidClock
import com.morningsteps.kids.platform.ChimePlayer

/** Manual dependency injection: one instance of each long-lived object for the whole app. */
class AppContainer(context: Context) {
    private val database: AppDatabase by lazy { AppDatabase.build(context) }
    val clock: AppClock = AndroidClock(context.applicationContext.contentResolver)
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val routines: RoutineRepository by lazy {
        RoutineRepository(
            tx = RoomTransactionRunner(database),
            routineDao = database.routineDao(),
            sessionDao = database.sessionDao(),
            historyDao = database.historyDao(),
            clock = clock,
        )
    }
    val chime: ChimePlayer by lazy { ChimePlayer(context) }
}
