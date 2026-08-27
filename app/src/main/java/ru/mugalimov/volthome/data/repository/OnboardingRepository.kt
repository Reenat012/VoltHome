package ru.mugalimov.volthome.data.repository

import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId

/**
 * Узкий репозиторий для commit 1.
 *
 * Без flow-сложности и без экранной интеграции:
 * coordinator-у сейчас нужны только простые persisted-операции.
 */
import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {

    suspend fun isShown(hintId: OnboardingHintId): Boolean

    fun observeShown(hintId: OnboardingHintId): Flow<Boolean>

    suspend fun markShown(hintId: OnboardingHintId)

    suspend fun getLastAnyHintShownAt(): Long

    suspend fun setLastAnyHintShownAt(value: Long)

    suspend fun areHintsEnabled(): Boolean

    suspend fun disableHints()

    suspend fun resetAll()
}
