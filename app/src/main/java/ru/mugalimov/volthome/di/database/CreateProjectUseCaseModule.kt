package ru.mugalimov.volthome.di.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.repository.UserPlanRepository
import ru.mugalimov.volthome.domain.use_case.CreateProjectUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CreateProjectUseCaseModule {

    @Provides
    @Singleton
    fun provideCreateProjectUseCase(
        projectsRepository: ProjectsRepository,
        userPlanRepository: UserPlanRepository,
    ): CreateProjectUseCase {
        return CreateProjectUseCase(
            projectsRepository = projectsRepository,
            planFlow = userPlanRepository.planFlow,
        )
    }
}