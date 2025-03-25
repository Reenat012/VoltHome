package ru.mugalimov.volthome.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.entity.LoadEntity
import ru.mugalimov.volthome.model.Load
import ru.mugalimov.volthome.model.Room

@Dao
interface LoadDao {
    //TODO что-то сомневаюсь что нужно связывать по roomId именно наблюдение
    @Query("SELECT * FROM loads ORDER BY created_at DESC")
    fun observeLoads() : Flow<List<LoadEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addLoad(loadEntity: LoadEntity)

    @Query("SELECT * FROM loads WHERE room_id = :roomId")
    fun getLoadForRoom(roomId: Int): Flow<Load>

    @Transaction
    @Query("SELECT * FROM rooms")
    fun getRoomsWithLoads(): Flow<List<RoomWithLoad>>
}

data class RoomWithLoad(
    @Embedded val room: Room,
    @Embedded val load: Load
)