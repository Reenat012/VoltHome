package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity

@Dao
interface RoomsTxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoom(room: RoomEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDevices(devices: List<DeviceEntity>): List<Long>

    @Transaction
    suspend fun insertRoomWithDevices(
        room: RoomEntity,
        devices: List<DeviceEntity>
    ): Pair<Long, List<Long>> {
        val roomId = insertRoom(room)
        val withFk = devices.map { it.copy(roomId = roomId) }
        val deviceIds = if (withFk.isNotEmpty()) insertDevices(withFk) else emptyList()
        return roomId to deviceIds
    }

    @Query("DELETE FROM devices WHERE device_id IN (:ids)")
    suspend fun deleteDevicesByIds(ids: List<Long>)
}