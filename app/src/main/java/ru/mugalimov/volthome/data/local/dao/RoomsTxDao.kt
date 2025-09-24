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
    // Ключевое изменение: IGNORE вместо ABORT, чтобы транзакция не «ломалась» на уникальном индексе.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
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
        // Если из-за уникального индекса (name, project_id) произошёл конфликт и вернулся -1,
        // вызывающий код должен обработать «комната уже существует».
        if (roomId <= 0L) return -1L to emptyList()

        val withFk = devices.map { it.copy(roomId = roomId) }
        val ids = insertDevicesInternal(withFk)
        return roomId to ids
    }

    @Transaction
    suspend fun insertDevices(entities: List<DeviceEntity>): List<Long> {
        return insertDevicesInternal(entities)
    }
}