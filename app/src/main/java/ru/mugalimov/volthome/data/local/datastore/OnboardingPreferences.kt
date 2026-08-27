package ru.mugalimov.volthome.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId

/**
 * Persisted storage только для onboarding-системы.
 *
 * Здесь намеренно нет:
 * - аналитических счётчиков
 * - targetTag
 * - runtime active state
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext context: Context
) {

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("volthome_onboarding_prefs") }
        )

    private companion object {
        val LAST_ANY_HINT_SHOWN_AT = longPreferencesKey("last_any_hint_shown_at")
        val HINTS_DISABLED = booleanPreferencesKey("hints_disabled")
    }

    suspend fun isShown(hintId: OnboardingHintId): Boolean {
        val key = booleanPreferencesKey("hint_shown_${hintId.storageKey}")
        return dataStore.data
            .map { prefs -> prefs[key] ?: false }
            .first()
    }

    fun observeShown(hintId: OnboardingHintId) =
        dataStore.data.map { prefs ->
            val key = booleanPreferencesKey("hint_shown_${hintId.storageKey}")
            prefs[key] ?: false
        }

    suspend fun markShown(hintId: OnboardingHintId) {
        val key = booleanPreferencesKey("hint_shown_${hintId.storageKey}")
        dataStore.edit { prefs ->
            prefs[key] = true
        }
    }

    suspend fun getLastAnyHintShownAt(): Long {
        return dataStore.data
            .map { prefs -> prefs[LAST_ANY_HINT_SHOWN_AT] ?: 0L }
            .first()
    }

    suspend fun setLastAnyHintShownAt(value: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_ANY_HINT_SHOWN_AT] = value
        }
    }

    suspend fun areHintsEnabled(): Boolean {
        return dataStore.data
            .map { prefs -> !(prefs[HINTS_DISABLED] ?: false) }
            .first()
    }

    suspend fun disableHints() {
        dataStore.edit { prefs ->
            prefs[HINTS_DISABLED] = true
        }
    }

    suspend fun resetAll() {
        dataStore.edit { prefs ->
            OnboardingHintId.entries.forEach { hintId ->
                prefs.remove(booleanPreferencesKey("hint_shown_${hintId.storageKey}"))
            }
            prefs.remove(LAST_ANY_HINT_SHOWN_AT)
            prefs.remove(HINTS_DISABLED)
        }
    }
}
