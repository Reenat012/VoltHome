package ru.mugalimov.volthome.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.domain.model.PhaseMode

class AppPreferences(private val context: Context) {

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("volthome_prefs") }
        )

    private companion object {
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val PHASE_MODE = stringPreferencesKey("phase_mode")

        // Hints (Loads -> Manual DnD)
        val MANUAL_MODE_HINT_SHOWN = booleanPreferencesKey("manual_mode_hint_shown")
        val FIRST_DRAG_HINT_SHOWN = booleanPreferencesKey("first_drag_hint_shown")
    }

    val ifFirstLaunch: Flow<Boolean> = dataStore.data
        .map { it[IS_FIRST_LAUNCH] ?: true }

    val phaseMode: Flow<PhaseMode> = dataStore.data
        .map { pref ->
            when (pref[PHASE_MODE]) {
                PhaseMode.SINGLE.name -> PhaseMode.SINGLE
                PhaseMode.THREE.name -> PhaseMode.THREE
                else -> PhaseMode.THREE // дефолт: 3 фазы
            }
        }

    // Hint 1 — первый вход в MANUAL
    val manualModeHintShown: Flow<Boolean> = dataStore.data
        .map { it[MANUAL_MODE_HINT_SHOWN] ?: false }

    // Hint 2 — первый активный drag
    val firstDragHintShown: Flow<Boolean> = dataStore.data
        .map { it[FIRST_DRAG_HINT_SHOWN] ?: false }

    suspend fun setFirstLaunchCompeted() {
        dataStore.edit { it[IS_FIRST_LAUNCH] = false }
    }

    suspend fun setPhaseMode(mode: PhaseMode) {
        dataStore.edit { it[PHASE_MODE] = mode.name }
    }

    suspend fun setManualModeHintShown() {
        dataStore.edit { it[MANUAL_MODE_HINT_SHOWN] = true }
    }

    suspend fun setFirstDragHintShown() {
        dataStore.edit { it[FIRST_DRAG_HINT_SHOWN] = true }
    }
}