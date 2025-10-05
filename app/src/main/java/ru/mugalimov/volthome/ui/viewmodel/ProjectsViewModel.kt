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

    private val activeIdFlow: Flow<String?> = activeDs.activeProjectId.distinctUntilChanged()

    /**
     * Порядок теперь отдаёт DAO (rowid ASC = порядок создания).
     * Никакой дополнительной сортировки по updatedAt в VM не делаем.
     */
    val projectsUi: Flow<List<ProjectUi>> =
        combine(repo.listProjects(), activeIdFlow) { list, activeId ->
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
            val title = name?.takeIf { it.isNotBlank() } ?: defaultProjectName()
            repo.createProject(title, note = null)

            // Активным делаем "самый новый" — в нашем случае это последний по rowid,
            // но безопаснее взять по updatedAt из доменной модели:
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
            val active = activeIdFlow.first()
            if (active == id) {
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

    private fun defaultProjectName(): String {
        val now = java.time.OffsetDateTime.now()
        return "Проект ${now.toLocalDate()} ${now.toLocalTime().withNano(0)}"
    }
}