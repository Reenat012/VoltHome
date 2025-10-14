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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoom(entity: RoomEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDevicesInternal(entities: List<DeviceEntity>): List<Long>

    @Query("DELETE FROM devices WHERE device_id IN (:deviceIds)")
    suspend fun deleteDevicesByIds(deviceIds: List<Long>)

    @Transaction
    suspend fun insertRoomWithDevices(
        room: RoomEntity,
        devices: List<DeviceEntity>
    ): Pair<Long, List<Long>> {
        android.util.Log.i("RoomsTxDao", "TX start")

        val roomId = insertRoom(room)
        android.util.Log.i("RoomsTxDao", "room inserted result=$roomId (<=0 means conflict)")

        if (roomId <= 0L) {
            android.util.Log.w("RoomsTxDao", "room conflict (unique name+project). returning -1")
            return -1L to emptyList()
        }

        val withFk = devices.map { it.copy(roomId = roomId) }
        android.util.Log.i("RoomsTxDao", "devices toInsert=${withFk.size}")

        val ids = insertDevicesInternal(withFk)
        android.util.Log.i("RoomsTxDao", "devices inserted count=${ids.size}")

        android.util.Log.i("RoomsTxDao", "TX end")
        return roomId to ids
    }

    // insertDevices тут @Transaction не нужен — это один вызов insert’а:
    suspend fun insertDevices(entities: List<DeviceEntity>): List<Long> {
        android.util.Log.i("RoomsTxDao", "insertDevices start size=${entities.size}")
        val ids = insertDevicesInternal(entities)
        android.util.Log.i("RoomsTxDao", "insertDevices end inserted=${ids.size}")
        return ids
    }
}