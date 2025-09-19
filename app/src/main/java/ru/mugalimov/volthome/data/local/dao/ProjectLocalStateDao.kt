package ru.mugalimov.volthome.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.ProjectLocalStateEntity

@Dao
interface ProjectLocalStateDao {
    @Query("SELECT * FROM project_local_state WHERE project_id=:projectId LIMIT 1")
    suspend fun get(projectId: String): ProjectLocalStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ProjectLocalStateEntity)

    @Query("UPDATE project_local_state SET remote_version=:remoteVersion WHERE project_id=:projectId")
    suspend fun updateRemoteVersion(projectId: String, remoteVersion: Int)

    @Query("UPDATE project_local_state SET last_sync_at=:ts WHERE project_id=:projectId")
    suspend fun updateLastSyncAt(projectId: String, ts: String?)

    @Query("UPDATE project_local_state SET has_local_changes=:flag WHERE project_id=:projectId")
    suspend fun setHasLocalChanges(projectId: String, flag: Boolean)

    @Query("SELECT * FROM project_local_state")
    fun observeAll(): Flow<List<ProjectLocalStateEntity>>
}