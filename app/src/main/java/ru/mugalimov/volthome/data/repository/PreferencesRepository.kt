package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    // Поток данных первого запуска
   val  isFirstLaunch: Flow<Boolean>

   // Метод для отметки о завершении онбординга
   suspend fun completeOnboarding()
}