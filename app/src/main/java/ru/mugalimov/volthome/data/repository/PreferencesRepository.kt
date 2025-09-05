package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.PhaseMode

interface PreferencesRepository {
    // Поток данных первого запуска
    val isFirstLaunch: Flow<Boolean>

    // Метод для отметки о завершении онбординга
    suspend fun completeOnboarding()

    val phaseMode: Flow<PhaseMode>
    suspend fun setPhaseMode(mode: PhaseMode)
}