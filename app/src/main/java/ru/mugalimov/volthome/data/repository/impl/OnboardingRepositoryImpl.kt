package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.local.datastore.OnboardingPreferences
import ru.mugalimov.volthome.data.repository.OnboardingRepository
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingHintId

/**
 * Тонкая реализация onboarding-репозитория поверх DataStore.
 */
@Singleton
class OnboardingRepositoryImpl @Inject constructor(
    private val preferences: OnboardingPreferences
) : OnboardingRepository {

    override suspend fun isShown(hintId: OnboardingHintId): Boolean {
        return preferences.isShown(hintId)
    }

    override fun observeShown(hintId: OnboardingHintId): Flow<Boolean> {
        return preferences.observeShown(hintId)
    }

    override suspend fun markShown(hintId: OnboardingHintId) {
        preferences.markShown(hintId)
    }

    override suspend fun getLastAnyHintShownAt(): Long {
        return preferences.getLastAnyHintShownAt()
    }

    override suspend fun setLastAnyHintShownAt(value: Long) {
        preferences.setLastAnyHintShownAt(value)
    }
}