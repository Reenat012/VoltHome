package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.ProjectSetupEntity

@Dao
interface ProjectSetupDao {
    @Query("SELECT * FROM project_setup WHERE project_id = :projectId LIMIT 1")
    suspend fun get(projectId: String): ProjectSetupEntity?

    @Query("SELECT * FROM project_setup WHERE project_id = :projectId LIMIT 1")
    fun observe(projectId: String): Flow<ProjectSetupEntity?>

    @Upsert
    suspend fun upsert(entity: ProjectSetupEntity)

    @Query("UPDATE project_setup SET phase_mode = :phaseMode WHERE project_id = :projectId")
    suspend fun updatePhaseMode(projectId: String, phaseMode: String): Int
}
