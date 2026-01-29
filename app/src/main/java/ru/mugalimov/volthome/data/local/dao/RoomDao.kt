package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.local.model.RoomWithDevicesPreviewDb

@Dao
interface RoomDao {

    /* ------------ OBSERVE / READ (tombstones filtered) ------------ */

    @Query("""
        SELECT * FROM rooms r
        WHERE r.project_id = :projectId
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'ROOM'
                AND t.local_id = r.id
          )
        ORDER BY r.created_at ASC, r.id ASC
    """)
    fun observeAllRoomsByProject(projectId: String): Flow<List<RoomEntity>>

    /**
     * Реактивная витрина RoomsList:
     * - devicesCount: COUNT(devices) по комнате
     * - preview: первые :previewLimit устройств по стабильному порядку (created_at, device_id)
     *
     * ВАЖНО: результат зависит от rooms + devices (+ tombstones), значит Flow эмитит при insert/delete/update devices.
     */
    @Query("""
    SELECT
        r.id AS roomId,
        r.name AS roomName,
        r.room_type AS roomType,

        -- полный счётчик устройств в комнате
        (
            SELECT COUNT(*)
            FROM devices d
            WHERE d.project_id = :projectId
              AND d.room_id = r.id
              AND NOT EXISTS (
                  SELECT 1 FROM tombstones t
                  WHERE t.entity_type = 'DEVICE'
                    AND t.local_id = d.device_id
              )
        ) AS devicesCount,

        -- превью: 1-е устройство
        (
            SELECT d1.name
            FROM devices d1
            WHERE d1.project_id = :projectId
              AND d1.room_id = r.id
              AND NOT EXISTS (
                  SELECT 1 FROM tombstones t
                  WHERE t.entity_type = 'DEVICE'
                    AND t.local_id = d1.device_id
              )
            ORDER BY d1.created_at ASC, d1.device_id ASC
            LIMIT 1 OFFSET 0
        ) AS previewName1,

        -- превью: 2-е устройство
        (
            SELECT d2.name
            FROM devices d2
            WHERE d2.project_id = :projectId
              AND d2.room_id = r.id
              AND NOT EXISTS (
                  SELECT 1 FROM tombstones t
                  WHERE t.entity_type = 'DEVICE'
                    AND t.local_id = d2.device_id
              )
            ORDER BY d2.created_at ASC, d2.device_id ASC
            LIMIT 1 OFFSET 1
        ) AS previewName2,

        -- превью: 3-е устройство
        (
            SELECT d3.name
            FROM devices d3
            WHERE d3.project_id = :projectId
              AND d3.room_id = r.id
              AND NOT EXISTS (
                  SELECT 1 FROM tombstones t
                  WHERE t.entity_type = 'DEVICE'
                    AND t.local_id = d3.device_id
              )
            ORDER BY d3.created_at ASC, d3.device_id ASC
            LIMIT 1 OFFSET 2
        ) AS previewName3

    FROM rooms r
    WHERE r.project_id = :projectId
      AND NOT EXISTS (
          SELECT 1 FROM tombstones t
          WHERE t.entity_type = 'ROOM'
            AND t.local_id = r.id
      )
    ORDER BY r.created_at ASC, r.id ASC
""")
    fun observeRoomsWithDevicesPreviewByProject(
        projectId: String
    ): Flow<List<RoomWithDevicesPreviewDb>>

    @Query("""
        SELECT * FROM rooms r
        WHERE r.id = :roomId
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'ROOM'
                AND t.local_id = r.id
          )
        LIMIT 1
    """)
    suspend fun getRoomById(roomId: Long): RoomEntity?

    @Query("""
        SELECT r.* FROM rooms r
        WHERE NOT EXISTS (
            SELECT 1 FROM tombstones t
            WHERE t.entity_type = 'ROOM'
              AND t.local_id = r.id
        )
        ORDER BY r.created_at ASC, r.id ASC
    """)
    suspend fun getAllRooms(): List<RoomEntity>

    @Query("""
        SELECT r.* FROM rooms r
        WHERE r.project_id = :projectId
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'ROOM'
                AND t.local_id = r.id
          )
        ORDER BY r.created_at ASC, r.id ASC
    """)
    suspend fun getAllRoomsByProject(projectId: String): List<RoomEntity>

    /* ------------ LOOKUPS ------------ */

    @Query("""
        SELECT id FROM rooms r
        WHERE r.project_id = :projectId
          AND r.name = :name
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'ROOM'
                AND t.local_id = r.id
          )
        LIMIT 1
    """)
    suspend fun findIdByProjectAndName(projectId: String, name: String): Long?

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM rooms r
            WHERE r.project_id = :projectId
              AND r.name = :name
              AND NOT EXISTS (
                  SELECT 1 FROM tombstones t
                  WHERE t.entity_type = 'ROOM'
                    AND t.local_id = r.id
              )
        )
    """)
    suspend fun existsByNameInProject(name: String, projectId: String): Boolean

    /* ------------ WRITE ------------ */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RoomEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRoom(entity: RoomEntity): Long

    @Update
    suspend fun updateRoom(entity: RoomEntity)

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoomById(roomId: Long): Int

    /* ------------ Bulk ops for project ------------ */

    @Query("UPDATE rooms SET project_id = :newProjectId WHERE project_id = :oldProjectId")
    suspend fun rebindProjectRooms(oldProjectId: String, newProjectId: String): Int

    @Query("DELETE FROM rooms WHERE project_id = :projectId")
    suspend fun deleteRoomsByProject(projectId: String)

    @Query("""
        SELECT COUNT(*) FROM rooms r
        WHERE r.project_id = :projectId
          AND NOT EXISTS (
              SELECT 1 FROM tombstones t
              WHERE t.entity_type = 'ROOM'
                AND t.local_id = r.id
          )
    """)
    suspend fun countByProjectId(projectId: String): Int
}