package ru.mugalimov.volthome.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.ProjectEntity

@Dao
interface ProjectDao {

    /**
     * Стабильный порядок по "первой вставке".
     * Важно: мы не используем REPLACE при апсёрте, чтобы rowid не менялся.
     */
    @Query("SELECT * FROM projects WHERE is_deleted = 0 ORDER BY rowid ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    // ---- Апсёрт без REPLACE: insertOrIgnore + update ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ProjectEntity): Long

    @Update
    suspend fun update(item: ProjectEntity)

    @Transaction
    suspend fun upsert(item: ProjectEntity) {
        val inserted = insert(item)
        if (inserted == -1L) {
            update(item)
        }
    }

    // ---- Остальные операции ----

    @Query("UPDATE projects SET name=:name, updated_at=:updatedAt, version=:version WHERE id=:id")
    suspend fun rename(id: String, name: String, updatedAt: String, version: Int)

    @Query("UPDATE projects SET is_deleted=1, updated_at=:updatedAt, version=:version WHERE id=:id")
    suspend fun softDelete(id: String, updatedAt: String, version: Int)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}