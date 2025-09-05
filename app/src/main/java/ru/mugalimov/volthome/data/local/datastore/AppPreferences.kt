package ru.mugalimov.volthome.data.local.datastore


import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
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
    }

    val ifFirstLaunch: Flow<Boolean> = dataStore.data
        .map { it[IS_FIRST_LAUNCH] ?: true }

    // Новое: поток режима
    val phaseMode: Flow<PhaseMode> = dataStore.data
        .map { pref ->
            when (pref[PHASE_MODE]) {
                PhaseMode.SINGLE.name -> PhaseMode.SINGLE
                PhaseMode.THREE.name  -> PhaseMode.THREE
                else                  -> PhaseMode.THREE // дефолт: 3 фазы
            }
        }

    suspend fun setFirstLaunchCompeted() {
        dataStore.edit { it[IS_FIRST_LAUNCH] = false }
    }

    // Новое: setter режима
    suspend fun setPhaseMode(mode: PhaseMode) {
        dataStore.edit { it[PHASE_MODE] = mode.name }
    }
}