package ru.mugalimov.volthome.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.domain.model.Project
import ru.mugalimov.volthome.ui.model.ProjectUi

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repo: ProjectsRepository,
    private val activeDs: ActiveProjectDataStore
) : ViewModel() {

    // Публичный поток активного проекта (важно для навигации из любых экранов)
    val activeProjectId: Flow<String?> = activeDs.activeProjectId.distinctUntilChanged()

    // НОВОЕ: заголовок для AppBar — имя активного проекта или "Проект не задан"
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
                    isActive = p.id == activeId
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createNewProject(name: String? = null) {
        viewModelScope.launch {
            val title = name?.takeIf { it.isNotBlank() } ?: run {
                val existing = repo.listProjects().first()
                nextSequentialProjectName(existing)
            }
            repo.createProject(title, note = null)

            // Активным делаем "самый новый"
            val newest: Project? = repo.listProjects().first().maxByOrNull { it.updatedAt }
            newest?.let {
                activeDs.setActiveProjectId(it.id)
                repo.openProject(it.id)
            }
        }
    }

    fun selectProject(id: String) {
        viewModelScope.launch {
            activeDs.setActiveProjectId(id)
            repo.openProject(id)
        }
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
}