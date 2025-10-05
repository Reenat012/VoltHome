package ru.mugalimov.volthome.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.TombstoneEntity
import ru.mugalimov.volthome.data.local.entity.TombstoneEntityType

@Dao
interface TombstoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TombstoneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<TombstoneEntity>): List<Long>

    @Delete
    suspend fun delete(entity: TombstoneEntity)

    @Query("DELETE FROM tombstones WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("""
        SELECT * FROM tombstones
        WHERE entity_type = :type AND project_id = :projectId
    """)
    suspend fun listByProject(type: TombstoneEntityType, projectId: String): List<TombstoneEntity>

    @Query("""
        SELECT * FROM tombstones
        WHERE entity_type = :type AND (local_id = :localId OR (:localId IS NULL AND local_id IS NULL))
        LIMIT 1
    """)
    suspend fun findByLocalId(type: TombstoneEntityType, localId: Long?): TombstoneEntity?

    @Query("""
        SELECT * FROM tombstones
        WHERE entity_type = :type AND server_uuid = :uuid
        LIMIT 1
    """)
    suspend fun findByServerUuid(type: TombstoneEntityType, uuid: String): TombstoneEntity?

    @Query("SELECT * FROM tombstones ORDER BY created_at DESC")
    fun observeAll(): Flow<List<TombstoneEntity>>
}