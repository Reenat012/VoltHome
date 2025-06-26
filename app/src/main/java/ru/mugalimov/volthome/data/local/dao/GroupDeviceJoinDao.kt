package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin

@Dao
interface GroupDeviceJoinDao {

    @Insert
    suspend fun insertJoin(join: GroupDeviceJoin)

    @Query("DELETE FROM group_device_join WHERE group_id IN (SELECT group_id FROM groups WHERE room_id = :roomId)")
    suspend fun deleteJoinsForRoom(roomId: Long)

    // Получает ID всех групп, связанных с устройством
    @Query("SELECT group_id FROM group_device_join WHERE device_id = :deviceId")
    suspend fun getGroupIdsForDevice(deviceId: Long): List<Long>

    // Удаляет все связи устройства с группами
    @Query("DELETE FROM group_device_join WHERE device_id = :deviceId")
    suspend fun deleteJoinsForDevice(deviceId: Long)

    @Query("SELECT d.* FROM devices d " +
            "INNER JOIN group_device_join j ON d.device_id = j.device_id " +
            "WHERE j.group_id = :groupId")
    suspend fun getDevicesForGroup(groupId: Long): List<DeviceEntity>
}