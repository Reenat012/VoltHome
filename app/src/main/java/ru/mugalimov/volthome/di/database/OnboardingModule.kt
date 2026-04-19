package ru.mugalimov.volthome.di.database

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.mugalimov.volthome.data.repository.OnboardingRepository
import ru.mugalimov.volthome.data.repository.impl.OnboardingRepositoryImpl

/**
 * DI-модуль onboarding commit 1.
 *
 * Здесь связываем только репозиторий.
 * Сам coordinator и preferences создаются через @Inject constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(
        impl: OnboardingRepositoryImpl
    ): OnboardingRepository
}