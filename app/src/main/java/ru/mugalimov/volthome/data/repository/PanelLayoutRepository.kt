package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot

interface PanelLayoutRepository {
    fun observe(projectId: String): Flow<PanelLayoutSnapshot?>
    suspend fun get(projectId: String): PanelLayoutSnapshot?
    suspend fun save(projectId: String, snapshot: PanelLayoutSnapshot)
    suspend fun delete(projectId: String)
}
