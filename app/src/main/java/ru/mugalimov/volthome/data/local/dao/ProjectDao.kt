package ru.mugalimov.volthome.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.ProjectEntity

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE is_deleted = 0 ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg items: ProjectEntity)

    @Query("UPDATE projects SET name=:name, updated_at=:updatedAt, version=:version WHERE id=:id")
    suspend fun rename(id: String, name: String, updatedAt: String, version: Int)

    @Query("UPDATE projects SET is_deleted=1, updated_at=:updatedAt, version=:version WHERE id=:id")
    suspend fun softDelete(id: String, updatedAt: String, version: Int)
}