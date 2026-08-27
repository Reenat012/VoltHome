package ru.mugalimov.volthome.data.repository.impl

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.local.dao.ApparatusSelectionDao
import ru.mugalimov.volthome.data.local.entity.ApparatusSelectionEntity
import ru.mugalimov.volthome.data.repository.PanelEquipmentRepository
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import javax.inject.Inject

class PanelEquipmentRepositoryImpl @Inject constructor(
    private val dao: ApparatusSelectionDao,
    private val gson: Gson
) : PanelEquipmentRepository {

    override fun observeSelections(
        projectId: String
    ): Flow<Map<String, SelectedApparatusSnapshot>> =
        dao.observeByProject(projectId.trim()).map(::decode)

    override suspend fun getSelections(
        projectId: String
    ): Map<String, SelectedApparatusSnapshot> = decode(dao.getByProject(projectId.trim()))

    override suspend fun saveSelection(
        projectId: String,
        snapshot: SelectedApparatusSnapshot
    ) {
        val normalizedProjectId = projectId.trim()
        require(normalizedProjectId.isNotBlank()) { "projectId must not be blank" }
        require(snapshot.slotId.isNotBlank()) { "slotId must not be blank" }
        dao.upsert(snapshot.toEntity(normalizedProjectId))
    }

    override suspend fun setUserPrice(projectId: String, slotId: String, priceKopecks: Long?) {
        require(priceKopecks == null || priceKopecks >= 0) { "Price must not be negative" }
        val entity = dao.get(projectId.trim(), slotId.trim()) ?: return
        val snapshot = runCatching {
            gson.fromJson(entity.snapshot_json, SelectedApparatusSnapshot::class.java)
        }.getOrNull() ?: return
        dao.upsert(
            snapshot.copy(userPriceKopecks = priceKopecks)
                .toEntity(projectId.trim(), System.currentTimeMillis())
        )
    }

    override suspend fun removeSelection(projectId: String, slotId: String) {
        dao.delete(projectId.trim(), slotId.trim())
    }

    override suspend fun clearProject(projectId: String) {
        dao.deleteByProject(projectId.trim())
    }

    private fun decode(
        entities: List<ApparatusSelectionEntity>
    ): Map<String, SelectedApparatusSnapshot> = entities.mapNotNull { entity ->
        runCatching {
            gson.fromJson(entity.snapshot_json, SelectedApparatusSnapshot::class.java)
        }.getOrNull()?.let { snapshot -> entity.slot_id to snapshot }
    }.toMap()

    private fun SelectedApparatusSnapshot.toEntity(
        projectId: String,
        updatedAt: Long = System.currentTimeMillis()
    ) = ApparatusSelectionEntity(
        project_id = projectId,
        slot_id = slotId,
        snapshot_json = gson.toJson(this),
        catalog_version = catalogVersion,
        updated_at_epoch_ms = updatedAt
    )
}
