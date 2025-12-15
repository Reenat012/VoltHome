package ru.mugalimov.volthome.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.GroupPhaseOverrideEntity

@Dao
interface GroupPhaseOverrideDao {

    @Query("""
        SELECT * FROM group_phase_overrides
        WHERE project_id = :projectId
    """)
    fun observeByProject(projectId: String): Flow<List<GroupPhaseOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupPhaseOverrideEntity)

    @Query("""
        DELETE FROM group_phase_overrides
        WHERE project_id = :projectId
    """)
    suspend fun deleteByProject(projectId: String)
}