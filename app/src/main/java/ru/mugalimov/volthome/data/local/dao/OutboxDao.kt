package ru.mugalimov.volthome.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.OutboxEntity
import ru.mugalimov.volthome.data.local.entity.OutboxState

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OutboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<OutboxEntity>): List<Long>

    @Update
    suspend fun update(entity: OutboxEntity)

    @Query("UPDATE outbox SET state = :state, attempt = attempt + 1, last_error = :error, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    suspend fun markAttempt(id: Long, state: OutboxState, error: String?)

    @Query("UPDATE outbox SET state = :state, last_error = NULL, updated_at = CURRENT_TIMESTAMP WHERE id IN (:ids)")
    suspend fun markState(ids: List<Long>, state: OutboxState)

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Delete
    suspend fun delete(entity: OutboxEntity)

    @Query("""
        SELECT * FROM outbox
        WHERE state = :state
        ORDER BY created_at ASC
        LIMIT :limit
    """)
    suspend fun pickByState(state: OutboxState, limit: Int = 100): List<OutboxEntity>

    @Query("""
        SELECT * FROM outbox
        WHERE state IN (:states)
        ORDER BY created_at ASC
        LIMIT :limit
    """)
    suspend fun pickByStates(states: List<OutboxState>, limit: Int = 100): List<OutboxEntity>

    @Query("""
        SELECT * FROM outbox
        WHERE state = :state AND project_id = :projectId
        ORDER BY created_at ASC
        LIMIT :limit
    """)
    suspend fun pickByProject(projectId: String, state: OutboxState, limit: Int = 200): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE state = :state")
    suspend fun countByState(state: OutboxState): Int

    @Query("SELECT * FROM outbox ORDER BY created_at DESC")
    fun observeAll(): Flow<List<OutboxEntity>>
}