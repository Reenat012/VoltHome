package ru.mugalimov.volthome.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore by preferencesDataStore("volthome_settings")

@Singleton
class ActiveProjectDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_ACTIVE = stringPreferencesKey("activeProjectId")

    val activeProjectId: Flow<String?> = context.appDataStore.data.map { it[KEY_ACTIVE] }

    suspend fun setActiveProjectId(id: String?) {
        context.appDataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_ACTIVE) else prefs[KEY_ACTIVE] = id
        }
    }
}