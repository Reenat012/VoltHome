package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import java.util.Date

/**
 * Транзакционный soft-delete каскадом:
 * - помечает комнату удалённой
 * - помечает все группы этой комнаты удалёнными
 * - помечает все устройства этой комнаты удалёнными
 *
 * Делается в одной БД-транзакции, чтобы Outbox увидел согласованный набор изменений.
 */
@Dao
abstract class RoomsCascadeDao {

    @Query("""
        UPDATE rooms
        SET is_deleted = 1,
            updated_at = :now
        WHERE id = :roomId AND project_id = :projectId AND is_deleted = 0
    """)
    protected abstract suspend fun markRoomDeleted(projectId: String, roomId: String, now: Date): Int

    @Query("""
        UPDATE groups
        SET is_deleted = 1,
            updated_at = :now
        WHERE project_id = :projectId
          AND room_id    = :roomId
          AND is_deleted = 0
    """)
    protected abstract suspend fun markGroupsDeleted(projectId: String, roomId: String, now: Date): Int

    @Query("""
        UPDATE devices
        SET is_deleted = 1,
            updated_at = :now
        WHERE project_id = :projectId
          AND room_id    = :roomId
          AND is_deleted = 0
    """)
    protected abstract suspend fun markDevicesDeleted(projectId: String, roomId: String, now: Date): Int

    /**
     * Публичная атомарная операция: soft-delete комнаты каскадом.
     * Возвращает сводку по затронутым строкам — можно логировать.
     */
    @Transaction
    open suspend fun deleteRoomCascade(
        projectId: String,
        roomId: String,
        now: Date = Date()
    ): DeleteRoomCascadeResult {
        val room = markRoomDeleted(projectId, roomId, now)
        val groups = markGroupsDeleted(projectId, roomId, now)
        val devices = markDevicesDeleted(projectId, roomId, now)
        android.util.Log.i(
            "RoomsCascadeDao",
            "deleteRoomCascade project=$projectId room=$roomId -> room=$room groups=$groups devices=$devices"
        )
        return DeleteRoomCascadeResult(roomUpdated = room, groupsUpdated = groups, devicesUpdated = devices)
    }

    data class DeleteRoomCascadeResult(
        val roomUpdated: Int,
        val groupsUpdated: Int,
        val devicesUpdated: Int
    )
}