package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.data.repository.PreferencesRepository

class PreferencesRepositoryImpl(private val appPreferences: AppPreferences) :
    PreferencesRepository {
    // Возвращаем поток статуса первого запуска
    override val isFirstLaunch: Flow<Boolean> = appPreferences.ifFirstLaunch

    // Сохраняем статус завершения онбординга
    override suspend fun completeOnboarding() = appPreferences.setFirstLaunchCompeted()
}