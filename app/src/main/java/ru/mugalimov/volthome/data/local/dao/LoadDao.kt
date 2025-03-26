package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.LoadEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.domain.model.Load
import ru.mugalimov.volthome.domain.model.Room
import ru.mugalimov.volthome.domain.model.RoomWithLoad

@Dao
interface LoadDao {
    //TODO что-то сомневаюсь что нужно связывать по roomId именно наблюдение
    @Query("SELECT * FROM loads ORDER BY created_at DESC")
    fun observeLoads() : Flow<List<LoadEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addLoad(loadEntity: LoadEntity)

    @Query("SELECT * FROM loads WHERE room_id = :roomId")
    fun getLoadForRoom(roomId: Long): Flow<List<LoadEntity>>

    @Transaction
    @Query("SELECT * FROM rooms")
    fun getRoomsWithLoads(): Flow<List<RoomWithLoad>>
}

