package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.datastore.AppPreferences
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.PhaseMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val appPreferences: AppPreferences) :
    PreferencesRepository {
    // Возвращаем поток статуса первого запуска
    override val isFirstLaunch: Flow<Boolean> = appPreferences.ifFirstLaunch

    // Сохраняем статус завершения онбординга
    override suspend fun completeOnboarding() = appPreferences.setFirstLaunchCompeted()

    override val phaseMode: Flow<PhaseMode> = appPreferences.phaseMode

    override suspend fun setPhaseMode(mode: PhaseMode) {
        appPreferences.setPhaseMode(mode)
    }
}