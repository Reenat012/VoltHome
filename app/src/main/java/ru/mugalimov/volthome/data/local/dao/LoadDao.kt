package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.domain.model.Load
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithLoad

@Dao
interface LoadDao {

    // --- НОВОЕ: проектный скоуп ---
    @Query("SELECT * FROM loads WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeLoadsByProject(projectId: String): Flow<List<LoadEntity>>

    // --- СТАРОЕ ---
    @Query("SELECT * FROM loads ORDER BY created_at DESC")
    fun observeLoads(): Flow<List<LoadEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addLoad(loadEntity: LoadEntity)

    @Query("SELECT * FROM loads WHERE room_id = :roomId")
    fun getLoadForRoom(roomId: Long): List<LoadEntity>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM rooms")
    fun getRoomsWithLoads(): Flow<List<RoomWithLoad>>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateLoad(load: LoadEntity): Int

    @Update
    suspend fun upsertLoad(load: LoadEntity) {
        if (updateLoad(load) == 0) {
            addLoad(load)
        }
    }

    @Transaction
    suspend fun updateAllLoads(loads: List<LoadEntity>) {
        loads.forEach { updateLoad(it) }
    }

    @Transaction
    suspend fun upsertAllLoads(loads: List<LoadEntity?>) {
        loads.forEach { load ->
            if (load != null) {
                if (load.id == 0L) addLoad(load) else updateLoad(load)
            }
        }
    }
}