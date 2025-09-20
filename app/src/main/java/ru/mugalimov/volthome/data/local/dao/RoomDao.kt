package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.domain.model.RoomWithDevicesEntity

@Dao
interface RoomDao {

    @Query("SELECT * FROM rooms ORDER BY created_at DESC")
    fun observeAllRooms(): Flow<List<RoomEntity>>

    // 🔹 проектный поток
    @Query("SELECT * FROM rooms WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeAllRoomsByProject(projectId: String): Flow<List<RoomEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addRoom(room: RoomEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateRoom(room: RoomEntity): Int

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoomById(roomId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM rooms WHERE name = :name LIMIT 1)")
    suspend fun existsByName(name: String): Boolean

    @Query("SELECT * FROM rooms WHERE id = :roomId")
    suspend fun getRoomById(roomId: Long): RoomEntity?

    @Transaction
    @Query("SELECT * FROM rooms WHERE id=:roomId")
    suspend fun getRoomWithDevicesById(roomId: Long): RoomWithDevicesEntity?

    @Transaction
    @Query("SELECT * FROM rooms ORDER BY created_at DESC")
    fun observeAllRoomsWithDevices(): List<RoomWithDevicesEntity>

    // 🔹 проектный вариант
    @Transaction
    @Query("SELECT * FROM rooms WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeAllRoomsWithDevicesByProject(projectId: String): List<RoomWithDevicesEntity>

    @Query("SELECT * FROM rooms")
    suspend fun getAllRooms(): List<RoomEntity>

    // 🔹 проектный вариант
    @Query("SELECT * FROM rooms WHERE project_id = :projectId")
    suspend fun getAllRoomsByProject(projectId: String): List<RoomEntity>

    @Query("SELECT COUNT(*) FROM rooms")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<RoomEntity>): List<Long>
}