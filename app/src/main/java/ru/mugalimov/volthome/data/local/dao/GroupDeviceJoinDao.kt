package ru.mugalimov.volthome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin

@Dao
interface GroupDeviceJoinDao {

    // ---- Observe ----
    @Query("SELECT * FROM group_device_join")
    fun observeJoins(): Flow<List<GroupDeviceJoin>>

    // ---- Insert ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJoin(join: GroupDeviceJoin)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(joins: List<GroupDeviceJoin>)

    // ---- Read helpers ----
    @Query("SELECT device_id FROM group_device_join WHERE group_id = :groupId")
    suspend fun getDeviceIdsForGroup(groupId: Long): List<Long>

    @Query("SELECT group_id FROM group_device_join WHERE device_id = :deviceId")
    suspend fun getGroupIdsForDevice(deviceId: Long): List<Long>

    @Query("""
        SELECT d.* FROM devices d
        INNER JOIN group_device_join j ON j.device_id = d.device_id
        WHERE j.group_id = :groupId
    """)
    suspend fun getDevicesForGroup(groupId: Long): List<DeviceEntity>

    // ---- Delete helpers ----
    @Query("DELETE FROM group_device_join")
    suspend fun deleteAll()

    @Query("DELETE FROM group_device_join WHERE device_id = :deviceId")
    suspend fun deleteJoinsForDevice(deviceId: Long)

    // удаление всех связей для групп, принадлежащих комнате
    @Query("""
        DELETE FROM group_device_join
        WHERE group_id IN (SELECT group_id FROM `groups` WHERE room_id = :roomId)
    """)
    suspend fun deleteJoinsForRoom(roomId: Long)
}