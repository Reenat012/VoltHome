package ru.mugalimov.volthome.data.local.datastore

import android.content.Context

import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.prefs.Preferences

class AppPreferences(context: Context) {
    // DataStore экземпляр через делегат
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("app_settings") }
    )

    // Ключи для хранения данных
    companion object {
        private val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
    }

    // Поток, возвращающий статус первого запуска
    // Если значение не задано - возвращает true (первый запуск)
    val ifFirstLaunch: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[IS_FIRST_LAUNCH] ?: true
        }

    // Сохраняем статус завершения онбординга
    // После вызова этого метода приложение будет считать, что первый запуск уже был
    suspend fun setFirstLaunchCompeted() {
        dataStore.edit { settings ->
            settings[IS_FIRST_LAUNCH] = false
        }
    }
}