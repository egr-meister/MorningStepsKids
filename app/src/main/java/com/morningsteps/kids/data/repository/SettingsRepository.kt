package com.morningsteps.kids.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class AppSettings(
    val selectedRoutineId: String? = null,
    /** Short bundled sound when a timer finishes. Off by default. */
    val timerSoundEnabled: Boolean = false,
    /** In-app switch to calm motion further (system "remove animations" is honored too). */
    val reduceMotion: Boolean = false,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val selectedRoutine = stringPreferencesKey("selected_routine_id")
        val timerSound = booleanPreferencesKey("timer_sound_enabled")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                selectedRoutineId = p[Keys.selectedRoutine],
                timerSoundEnabled = p[Keys.timerSound] ?: false,
                reduceMotion = p[Keys.reduceMotion] ?: false,
            )
        }

    suspend fun selectRoutine(id: String) {
        store.edit { it[Keys.selectedRoutine] = id }
    }

    suspend fun setTimerSound(enabled: Boolean) {
        store.edit { it[Keys.timerSound] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        store.edit { it[Keys.reduceMotion] = enabled }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }
}
