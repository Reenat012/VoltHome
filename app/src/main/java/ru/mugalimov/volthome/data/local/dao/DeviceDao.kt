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
    @Query("SELECT * FROM devices ORDER BY created_at, device_id")
    fun observeAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE room_id=:roomId ORDER BY created_at, device_id")
    fun observeDevicesByIdRoom(roomId: Long): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE device_id=:deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: Int): DeviceEntity?

    @Query("SELECT * FROM devices WHERE room_id=:roomId ORDER BY created_at, device_id")
    suspend fun getAllDevicesByRoomId(roomId: Long): List<DeviceEntity>

    @Query("SELECT * FROM devices ORDER BY created_at, device_id")
    suspend fun getAllDevices(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE room_id=:roomId AND name=:name LIMIT 1")
    suspend fun findByRoomAndName(roomId: Long, name: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addDevice(entity: DeviceEntity): Long

    @Update
    suspend fun update(entity: DeviceEntity)

    @Query("DELETE FROM devices WHERE device_id IN (:ids)")
    suspend fun deleteDevicesByIds(ids: List<Long>)

    @Query("DELETE FROM devices WHERE device_id=:deviceId")
    suspend fun deleteDeviceById(deviceId: Long): Int

    @Query("UPDATE devices SET project_id=:newProjectId WHERE project_id=:oldProjectId")
    suspend fun rebindProjectDevices(oldProjectId: String, newProjectId: String): Int

    @Query("DELETE FROM devices WHERE project_id=:projectId")
    suspend fun deleteDevicesByProject(projectId: String)

    @Query("SELECT * FROM devices WHERE project_id=:projectId ORDER BY created_at, device_id")
    suspend fun getAllDevicesByProject(projectId: String): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE project_id=:projectId ORDER BY created_at, device_id")
    fun observeAllDevicesByProject(projectId: String): Flow<List<DeviceEntity>>

    @Query("SELECT COUNT(*) FROM devices WHERE project_id=:projectId")
    suspend fun countByProjectId(projectId: String): Int

    @Query("SELECT * FROM devices WHERE device_id IN (:ids)")
    suspend fun getDevicesByIds(ids: List<Long>): List<DeviceEntity>

    @Query("SELECT COUNT(*) FROM devices WHERE project_id=:projectId")
    suspend fun countActiveByProjectId(projectId: String): Int

    @Query("SELECT device_id FROM devices WHERE project_id=:projectId ORDER BY created_at, device_id")
    suspend fun getActiveIdsByProjectId(projectId: String): List<Long>
}
