package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.flow.StateFlow
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.domain.config.ProjectsLimitConfig
import ru.mugalimov.volthome.domain.errors.ProjectLimitError
import ru.mugalimov.volthome.domain.errors.ProjectLimitError.ProjectLimitReached
import ru.mugalimov.volthome.domain.model.UserPlan

/**
 * ЕДИНСТВЕННАЯ точка проверки лимита проектов.
 *
 * UI / ViewModel не должны "решать можно/нельзя" — только отображать outcome.
 */
class CreateProjectUseCase(
    private val projectsRepository: ProjectsRepository,
    private val planFlow: StateFlow<UserPlan>,
) {

    sealed interface Outcome {
        data class Success(val projectId: String) : Outcome
        data class Failure(val error: ProjectLimitError) : Outcome
    }

    suspend operator fun invoke(
        name: String,
        note: String? = null,
    ): Outcome {
        val plan = planFlow.value
        val capabilities = plan.capabilities

        val limit = capabilities.projectsLimit()
        if (limit != ProjectsLimitConfig.UNLIMITED) {
            val current = projectsRepository.countActiveProjects()
            if (current >= limit) {
                return Outcome.Failure(
                    ProjectLimitReached(
                        limit = limit,
                        current = current,
                        isPro = plan.isPro,
                    )
                )
            }
        }

        val id = projectsRepository.createProject(name = name, note = note)
        return Outcome.Success(projectId = id)
    }
}