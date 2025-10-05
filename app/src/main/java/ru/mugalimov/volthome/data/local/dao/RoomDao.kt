package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.RoomEntity

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