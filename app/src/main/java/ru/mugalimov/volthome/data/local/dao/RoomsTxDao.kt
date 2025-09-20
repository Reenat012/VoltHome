package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity

/**
 * Транзакционные операции для комнаты и её устройств.
 * Используется RoomRepositoryImpl.
 */
@Dao
interface RoomsTxDao {

    // --- базовые вставки/удаления ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoom(entity: RoomEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDevicesInternal(entities: List<DeviceEntity>): List<Long>

    @Query("DELETE FROM devices WHERE device_id IN (:deviceIds)")
    suspend fun deleteDevicesByIds(deviceIds: List<Long>)

    // --- транзакции высокого уровня ---
    @Transaction
    suspend fun insertRoomWithDevices(
        room: RoomEntity,
        devices: List<DeviceEntity>
    ): Pair<Long, List<Long>> {
        val roomId = insertRoom(room)
        val withFk = devices.map { it.copy(roomId = roomId) }
        val ids = insertDevicesInternal(withFk)
        return roomId to ids
    }

    @Transaction
    suspend fun insertDevices(entities: List<DeviceEntity>): List<Long> {
        return insertDevicesInternal(entities)
    }
}