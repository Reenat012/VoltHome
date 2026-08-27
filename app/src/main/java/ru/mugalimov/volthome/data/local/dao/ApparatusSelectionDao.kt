package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.ApparatusSelectionEntity

@Dao
interface ApparatusSelectionDao {

    @Query("SELECT * FROM apparatus_selections WHERE project_id = :projectId ORDER BY slot_id")
    fun observeByProject(projectId: String): Flow<List<ApparatusSelectionEntity>>

    @Query("SELECT * FROM apparatus_selections WHERE project_id = :projectId ORDER BY slot_id")
    suspend fun getByProject(projectId: String): List<ApparatusSelectionEntity>

    @Query(
        "SELECT * FROM apparatus_selections " +
            "WHERE project_id = :projectId AND slot_id = :slotId LIMIT 1"
    )
    suspend fun get(projectId: String, slotId: String): ApparatusSelectionEntity?

    @Upsert
    suspend fun upsert(entity: ApparatusSelectionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ApparatusSelectionEntity>)

    @Query("DELETE FROM apparatus_selections WHERE project_id = :projectId AND slot_id = :slotId")
    suspend fun delete(projectId: String, slotId: String): Int

    @Query("DELETE FROM apparatus_selections WHERE project_id = :projectId")
    suspend fun deleteByProject(projectId: String): Int
}
