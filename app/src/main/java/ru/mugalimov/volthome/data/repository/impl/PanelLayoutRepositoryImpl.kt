package ru.mugalimov.volthome.data.repository.impl

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.local.dao.PanelLayoutDao
import ru.mugalimov.volthome.data.local.entity.PanelLayoutEntity
import ru.mugalimov.volthome.data.repository.PanelLayoutRepository
import ru.mugalimov.volthome.domain.model.panel.PanelLayoutSnapshot
import javax.inject.Inject

class PanelLayoutRepositoryImpl @Inject constructor(
    private val dao: PanelLayoutDao,
    private val gson: Gson
) : PanelLayoutRepository {

    private val codec = PanelLayoutSnapshotJsonCodec(gson)

    override fun observe(projectId: String): Flow<PanelLayoutSnapshot?> =
        dao.observe(projectId.trim()).map { it?.decode() }

    override suspend fun get(projectId: String): PanelLayoutSnapshot? =
        dao.get(projectId.trim())?.decode()

    override suspend fun save(projectId: String, snapshot: PanelLayoutSnapshot) {
        val normalizedProjectId = projectId.trim()
        require(normalizedProjectId.isNotBlank()) { "projectId must not be blank" }
        require(snapshot.schemaVersion == PanelLayoutSnapshot.CURRENT_SCHEMA_VERSION) {
            "Unsupported panel layout schema ${snapshot.schemaVersion}"
        }
        dao.upsert(
            PanelLayoutEntity(
                project_id = normalizedProjectId,
                snapshot_json = codec.encode(snapshot),
                schema_version = snapshot.schemaVersion,
                updated_at_epoch_ms = snapshot.updatedAtEpochMs
            )
        )
    }

    override suspend fun delete(projectId: String) {
        dao.delete(projectId.trim())
    }

    private fun PanelLayoutEntity.decode(): PanelLayoutSnapshot? = codec.decode(snapshot_json)
}
