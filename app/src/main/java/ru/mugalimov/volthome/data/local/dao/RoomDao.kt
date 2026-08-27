package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.local.model.RoomWithDevicesPreviewDb
import ru.mugalimov.volthome.domain.model.RoomWithDevicesEntity

@Dao
interface RoomDao {

    @Query("SELECT * FROM rooms WHERE project_id=:projectId ORDER BY created_at, id")
    fun observeAllRoomsByProject(projectId: String): Flow<List<RoomEntity>>

    /**
     * Один проход по rooms. Три коротких lookup используют покрывающий индекс
     * devices(project_id, room_id, created_at, device_id); sync-tombstones больше нет.
     */
    @Query(
        """
        SELECT r.id AS roomId, r.name AS roomName, r.room_type AS roomType,
               COUNT(d.device_id) AS devicesCount,
               (SELECT p.name FROM devices p WHERE p.project_id=:projectId AND p.room_id=r.id
                ORDER BY p.created_at, p.device_id LIMIT 1 OFFSET 0) AS previewName1,
               (SELECT p.name FROM devices p WHERE p.project_id=:projectId AND p.room_id=r.id
                ORDER BY p.created_at, p.device_id LIMIT 1 OFFSET 1) AS previewName2,
               (SELECT p.name FROM devices p WHERE p.project_id=:projectId AND p.room_id=r.id
                ORDER BY p.created_at, p.device_id LIMIT 1 OFFSET 2) AS previewName3
        FROM rooms r
        LEFT JOIN devices d ON d.project_id=:projectId AND d.room_id=r.id
        WHERE r.project_id=:projectId
        GROUP BY r.id, r.name, r.room_type, r.created_at
        ORDER BY r.created_at, r.id
        """
    )
    fun observeRoomsWithDevicesPreviewByProject(projectId: String): Flow<List<RoomWithDevicesPreviewDb>>

    @Query("SELECT * FROM rooms WHERE id=:roomId LIMIT 1")
    suspend fun getRoomById(roomId: Long): RoomEntity?

    @Query("SELECT * FROM rooms ORDER BY created_at, id")
    suspend fun getAllRooms(): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE project_id=:projectId ORDER BY created_at, id")
    suspend fun getAllRoomsByProject(projectId: String): List<RoomEntity>

    @Transaction
    @Query("SELECT * FROM rooms WHERE project_id=:projectId ORDER BY created_at, id")
    suspend fun getRoomsWithDevicesByProject(projectId: String): List<RoomWithDevicesEntity>

    @Query("SELECT id FROM rooms WHERE project_id=:projectId AND name=:name LIMIT 1")
    suspend fun findIdByProjectAndName(projectId: String, name: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM rooms WHERE project_id=:projectId AND name=:name)")
    suspend fun existsByNameInProject(name: String, projectId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(list: List<RoomEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addRoom(entity: RoomEntity): Long

    @Update
    suspend fun updateRoom(entity: RoomEntity)

    @Query("DELETE FROM rooms WHERE id=:roomId")
    suspend fun deleteRoomById(roomId: Long): Int

    @Query("UPDATE rooms SET project_id=:newProjectId WHERE project_id=:oldProjectId")
    suspend fun rebindProjectRooms(oldProjectId: String, newProjectId: String): Int

    @Query("DELETE FROM rooms WHERE project_id=:projectId")
    suspend fun deleteRoomsByProject(projectId: String)

    @Query("SELECT COUNT(*) FROM rooms WHERE project_id=:projectId")
    suspend fun countByProjectId(projectId: String): Int
}
