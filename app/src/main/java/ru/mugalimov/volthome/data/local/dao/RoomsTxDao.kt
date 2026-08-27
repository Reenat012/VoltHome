package ru.mugalimov.volthome.data.local.dao

import android.util.Log
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDevicesInternal(entities: List<DeviceEntity>): List<Long>

    @Query("DELETE FROM devices WHERE device_id IN (:deviceIds)")
    suspend fun deleteDevicesByIds(deviceIds: List<Long>)

    @Transaction
    suspend fun insertRoomWithDevices(
        room: RoomEntity,
        devices: List<DeviceEntity>
    ): Pair<Long, List<Long>> {
        Log.i("RoomsTxDao", "TX start")

        val roomId = insertRoom(room)
        Log.i("RoomsTxDao", "room inserted result=$roomId (<=0 means conflict)")

        // ✅ Commit 6: НИКАКИХ "тихих -1". Конфликт = ошибка (repo превратит в понятную UI-ошибку).
        require(roomId > 0L) { "ROOM_TX failed: insertRoom returned roomId=$roomId (conflict?)" }

        val withFk = devices.map { it.copy(roomId = roomId) }
        Log.i("RoomsTxDao", "devices toInsert=${withFk.size}")

        val ids = insertDevicesInternal(withFk)
        android.util.Log.i("RoomsTxDao", "devices inserted count=${ids.size}")

        // ✅ Commit 6: инвариант соответствия количества
        require(ids.size == withFk.size) {
            "ROOM_TX invariant failed: insertedIds=${ids.size} expected=${withFk.size} roomId=$roomId"
        }

        android.util.Log.i("RoomsTxDao", "TX end")
        return roomId to ids
    }

    /**
     * Мастер проекта создаёт несколько помещений одной транзакцией. Если любой
     * insert не прошёл, Room откатывает весь набор и пользователь не получает
     * наполовину созданный объект.
     */
    @Transaction
    suspend fun insertRoomsWithDevices(
        bundles: List<RoomInsertBundle>
    ): List<Pair<Long, List<Long>>> = bundles.map { bundle ->
        insertRoomWithDevices(bundle.room, bundle.devices)
    }

    // insertDevices тут @Transaction не нужен — это один вызов insert’а:
    suspend fun insertDevices(entities: List<DeviceEntity>): List<Long> {
        android.util.Log.i("RoomsTxDao", "insertDevices start size=${entities.size}")
        val ids = insertDevicesInternal(entities)



// никаких -1 и 0 — иначе это не insert
        require(ids.isNotEmpty()) { "DEV_TX failed: insertedIds empty size=${entities.size}" }
        require(ids.all { it > 0L }) { "DEV_TX failed: has non-positive ids=$ids size=${entities.size}" }

        Log.i("RoomsTxDao", "insertDevices end inserted=${ids.size}")
        return ids


    }
}

data class RoomInsertBundle(
    val room: RoomEntity,
    val devices: List<DeviceEntity>
)
