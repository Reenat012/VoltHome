package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot

interface PanelEquipmentRepository {
    fun observeSelections(projectId: String): Flow<Map<String, SelectedApparatusSnapshot>>
    suspend fun getSelections(projectId: String): Map<String, SelectedApparatusSnapshot>
    suspend fun saveSelection(projectId: String, snapshot: SelectedApparatusSnapshot)
    suspend fun setUserPrice(projectId: String, slotId: String, priceKopecks: Long?)
    suspend fun removeSelection(projectId: String, slotId: String)
    suspend fun clearProject(projectId: String)
}
