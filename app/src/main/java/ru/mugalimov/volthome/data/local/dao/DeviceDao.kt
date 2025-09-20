package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.DeviceEntity

@Dao
interface DeviceDao {

    // --- НОВОЕ: проектный скоуп ---
    @Query("SELECT * FROM devices WHERE project_id = :projectId")
    fun observeDevicesByProject(projectId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE project_id = :projectId")
    suspend fun getAllDevicesByProject(projectId: String): List<DeviceEntity>

    // --- СТАРОЕ (на переходный период) ---
    @Query("SELECT * FROM devices WHERE room_id = :roomId")
    fun observeDevicesByIdRoom(roomId: Long): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices")
    fun observeDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addDevice(deviceEntity: DeviceEntity)

    @Query("DELETE FROM devices WHERE device_id = :deviceId")
    suspend fun deleteDeviceById(deviceId: Long): Int

    @Query("SELECT * FROM devices WHERE device_id = :deviceId")
    suspend fun getDeviceById(deviceId: Int): DeviceEntity?

    @Query("SELECT * FROM devices WHERE room_id = :roomId")
    suspend fun getAllDevicesByRoomId(roomId: Long): List<DeviceEntity>

    @Query("SELECT * FROM devices")
    suspend fun getAllDevices(): List<DeviceEntity>

    @Query("""
        SELECT * FROM devices 
        WHERE device_id IN (
            SELECT device_id FROM group_device_join 
            WHERE group_id = :groupId
        )
    """)
    suspend fun getDevicesForGroup(groupId: Long): List<DeviceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<DeviceEntity>): List<Long>

    @Update
    suspend fun update(entity: DeviceEntity)

    @Query("SELECT * FROM devices WHERE room_id = :roomId AND name = :name LIMIT 1")
    suspend fun findByRoomAndName(roomId: Long, name: String): DeviceEntity?
}