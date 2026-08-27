package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.core.analytics.AnalyticsEvent
import ru.mugalimov.volthome.core.analytics.AnalyticsMode
import ru.mugalimov.volthome.core.analytics.AnalyticsTracker
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.domain.errors.ProjectLimitError.ProjectLimitReached
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.Project
import ru.mugalimov.volthome.domain.use_case.CreateProjectUseCase
import ru.mugalimov.volthome.domain.use_case.EnsureCalculationVersionUseCase
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.onboarding.model.ProjectsOnboardingFacts
import ru.mugalimov.volthome.ui.paywall.PaywallBus

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repo: ProjectsRepository,
    private val activeDs: ActiveProjectDataStore,
    private val paywallBus: PaywallBus,
    private val createProjectUseCase: CreateProjectUseCase,
    private val analytics: AnalyticsTracker,
    private val preferencesRepository: PreferencesRepository,
    private val projectSetupRepository: ProjectSetupRepository,
    private val ensureCalculationVersion: EnsureCalculationVersionUseCase,
) : ViewModel() {

    /**
     * UI-одноразовые сообщения (без привязки к конкретному UI-виджету).
     * Подключение покроем на следующем шаге/если уже есть инфраструктура.
     */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    // Публичный поток активного проекта (важно для навигации из любых экранов)
    val activeProjectId: Flow<String?> = activeDs.activeProjectId.distinctUntilChanged()

    // Заголовок для AppBar — имя активного проекта или "Проект не задан"
    val activeProjectTitle =
        combine(repo.listProjects(), activeProjectId) { list, activeId ->
            list.firstOrNull { it.id == activeId }?.name ?: "Проект не задан"
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Проект не задан")

    /**
     * Порядок отдаёт DAO (rowid ASC = порядок создания).
     * Без дополнительной сортировки по updatedAt.
     */
    val projectsUi: Flow<List<ProjectUi>> =
        combine(repo.listProjects(), activeProjectId) { list, activeId ->
            list.map { p ->
                ProjectUi(
                    id = p.id,
                    name = p.name,
                    isActive = p.id == activeId,
                    isDeleted = p.isDeleted
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Канонический источник фактов для Projects onboarding.
     *
     * Важно:
     * - используем только projectsUi
     * - не плодим второй источник истины
     */
    val onboardingFacts =
        repo.listProjects()
            .map { list ->
                ProjectsOnboardingFacts(
                    projectsCount = list.size,
                    isLoading = false
                )
            }
            .onStart { emit(ProjectsOnboardingFacts(isLoading = true)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ProjectsOnboardingFacts()
            )

    fun createNewProject(name: String? = null) {
        viewModelScope.launch {
            val existing = repo.listProjects().first()
            val title = name?.takeIf { it.isNotBlank() } ?: nextSequentialProjectName(existing)

            when (val outcome = createProjectUseCase(name = title, note = null)) {
                is CreateProjectUseCase.Outcome.Success -> {
                    analytics.track(
                        AnalyticsEvent.ProjectCreated(mode = AnalyticsMode.AUTOMATIC)
                    )
                    // Активируем локально созданный проект.
                    activeDs.setActiveProjectId(outcome.projectId)
                    repo.openProject(outcome.projectId)
                }

                is CreateProjectUseCase.Outcome.Failure -> {
                    when (outcome.error) {
                        is ProjectLimitReached -> {
                            // PRO CTA + короткое объяснение (текст можно отточить позже)
                            messages.tryEmit("Достигнут лимит Free по проектам. Для снятия лимита нужен PRO.")
                            paywallBus.request(ProFeature.PROJECTS_LIMIT)
                        }
                    }
                }
            }
        }
    }

    fun selectProject(id: String) {
        viewModelScope.launch {
            synchronizeProjectSettings(id)
            activeDs.setActiveProjectId(id)
            repo.openProject(id)
        }
    }

    fun synchronizeActiveProjectSettings(id: String) {
        viewModelScope.launch { synchronizeProjectSettings(id) }
    }

    fun renameProject(id: String, newName: String) {
        viewModelScope.launch { repo.renameProject(id, newName) }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repo.deleteProject(id)
            val current = activeProjectId.first()
            if (current == id) {
                val next = repo.listProjects().first().firstOrNull { !it.isDeleted }
                if (next != null) {
                    synchronizeProjectSettings(next.id)
                    activeDs.setActiveProjectId(next.id)
                    repo.openProject(next.id)
                } else {
                    activeDs.setActiveProjectId(null)
                }
            }
        }
    }

    // ---- Локальный helper: «Проект №N» по списку доменных проектов ----
    private fun nextSequentialProjectName(existing: List<Project>): String {
        val re = Regex("""^Проект №(\d+)$""")
        val max = existing
            .asSequence()
            .mapNotNull { p -> re.find(p.name)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "Проект №${max + 1}"
    }

    private suspend fun synchronizeProjectSettings(projectId: String) {
        val fallback = preferencesRepository.phaseMode.first()
        val setup = projectSetupRepository.getOrCreate(projectId, fallback)
        if (fallback != setup.phaseMode) preferencesRepository.setPhaseMode(setup.phaseMode)
        ensureCalculationVersion.execute(projectId)
    }
}
