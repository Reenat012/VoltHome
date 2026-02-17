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

    /* ------------ OBSERVE / READ (tombstones filtered) ------------ */

    @Query("""
        SELECT d.* FROM devices d
        WHERE NOT EXISTS (
            SELECT 1 FROM tombstones t
            WHERE t.entity_type = 'DEVICE'
              AND t.local_id = d.device_id
        )
        ORDER BY d.created_at ASC, d.device_id ASC
    """)
    fun observeAllDevices(): Flow<List<DeviceEntity>>

    @Query("""
        SELECT d.* FROM devices d
        WHERE d.room_id = :roomId
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'DEVICE'
                AND t.local_id = d.device_id
          )
        ORDER BY d.created_at ASC, d.device_id ASC
    """)
    fun observeDevicesByIdRoom(roomId: Long): Flow<List<DeviceEntity>>

    @Query("""
        SELECT d.* FROM devices d
        WHERE d.device_id = :deviceId
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'DEVICE'
                AND t.local_id = d.device_id
          )
        LIMIT 1
    """)
    suspend fun getDeviceById(deviceId: Int): DeviceEntity?

    @Query("""
        SELECT d.* FROM devices d
        WHERE d.room_id = :roomId
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'DEVICE'
                AND t.local_id = d.device_id
          )
        ORDER BY d.created_at ASC, d.device_id ASC
    """)
    suspend fun getAllDevicesByRoomId(roomId: Long): List<DeviceEntity>

    @Query("""
        SELECT d.* FROM devices d
        WHERE NOT EXISTS (
            SELECT 1 FROM tombstones t
            WHERE t.entity_type = 'DEVICE'
              AND t.local_id = d.device_id
        )
        ORDER BY d.created_at ASC, d.device_id ASC
    """)
    suspend fun getAllDevices(): List<DeviceEntity>

    /* ------------ LOOKUPS ------------ */

    @Query("""
        SELECT * FROM devices d
        WHERE d.room_id = :roomId
          AND d.name = :name
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'DEVICE'
                AND t.local_id = d.device_id
          )
        LIMIT 1
    """)
    suspend fun findByRoomAndName(roomId: Long, name: String): DeviceEntity?

    /* ------------ WRITE ------------ */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDevice(entity: DeviceEntity): Long

    @Update
    suspend fun update(entity: DeviceEntity)

    @Query("DELETE FROM devices WHERE device_id IN (:ids)")
    suspend fun deleteDevicesByIds(ids: List<Long>)

    @Query("DELETE FROM devices WHERE device_id = :deviceId")
    suspend fun deleteDeviceById(deviceId: Long): Int

    /* ------------ Bulk ops for project ------------ */

    @Query("UPDATE devices SET project_id = :newProjectId WHERE project_id = :oldProjectId")
    suspend fun rebindProjectDevices(oldProjectId: String, newProjectId: String): Int

    @Query("DELETE FROM devices WHERE project_id = :projectId")
    suspend fun deleteDevicesByProject(projectId: String)

    /* ------------ Project-scoped fetch ------------ */

    @Query("""
        SELECT d.* FROM devices d
        WHERE d.project_id = :projectId
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'DEVICE'
                AND t.local_id = d.device_id
          )
        ORDER BY d.created_at ASC, d.device_id ASC
    """)
    suspend fun getAllDevicesByProject(projectId: String): List<DeviceEntity>

    @Query("""
    SELECT COUNT(*) FROM devices d
    WHERE d.project_id = :projectId
""")
    suspend fun countByProjectId(projectId: String): Int

    @Query("""
        SELECT d.* FROM devices d
        WHERE d.device_id IN (:ids)
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'DEVICE'
                AND t.local_id = d.device_id
          )
    """)
    suspend fun getDevicesByIds(ids: List<Long>): List<DeviceEntity>
}