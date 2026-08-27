package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.CableLineCalculationEntity
import ru.mugalimov.volthome.data.local.entity.ProjectCableDefaultsEntity

@Dao
interface CableCalculationDao {
    @Query("SELECT * FROM project_cable_defaults WHERE project_id = :projectId LIMIT 1")
    suspend fun getDefaults(projectId: String): ProjectCableDefaultsEntity?

    @Query("SELECT * FROM project_cable_defaults WHERE project_id = :projectId LIMIT 1")
    fun observeDefaults(projectId: String): Flow<ProjectCableDefaultsEntity?>

    @Upsert
    suspend fun upsertDefaults(entity: ProjectCableDefaultsEntity)

    @Query("SELECT * FROM cable_line_calculations WHERE project_id = :projectId")
    fun observeCalculations(projectId: String): Flow<List<CableLineCalculationEntity>>

    @Query("SELECT * FROM cable_line_calculations WHERE project_id = :projectId")
    suspend fun getCalculations(projectId: String): List<CableLineCalculationEntity>

    @Query("SELECT * FROM cable_line_calculations WHERE group_id = :groupId LIMIT 1")
    suspend fun getCalculation(groupId: Long): CableLineCalculationEntity?

    @Upsert
    suspend fun upsertCalculation(entity: CableLineCalculationEntity)

    @Upsert
    suspend fun upsertCalculations(entities: List<CableLineCalculationEntity>)

    @Query("DELETE FROM cable_line_calculations WHERE group_id = :groupId")
    suspend fun deleteCalculation(groupId: Long)

    @Query("DELETE FROM cable_line_calculations WHERE project_id = :projectId")
    suspend fun deleteCalculations(projectId: String): Int
}
