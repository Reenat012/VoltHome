package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.Project

interface ProjectsRepository {
    fun listProjects(): Flow<List<Project>>
    suspend fun createProject(name: String, note: String? = null)
    suspend fun renameProject(id: String, name: String)
    suspend fun deleteProject(id: String)
    suspend fun openProject(id: String)
}