package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.PanelLayoutEntity

@Dao
interface PanelLayoutDao {
    @Query("SELECT * FROM panel_layouts WHERE project_id = :projectId LIMIT 1")
    fun observe(projectId: String): Flow<PanelLayoutEntity?>

    @Query("SELECT * FROM panel_layouts WHERE project_id = :projectId LIMIT 1")
    suspend fun get(projectId: String): PanelLayoutEntity?

    @Upsert
    suspend fun upsert(entity: PanelLayoutEntity)

    @Query("DELETE FROM panel_layouts WHERE project_id = :projectId")
    suspend fun delete(projectId: String): Int
}
