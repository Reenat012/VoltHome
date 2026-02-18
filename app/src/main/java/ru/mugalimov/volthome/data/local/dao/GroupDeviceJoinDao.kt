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

    /**
     * Таблица `group_device_join` — **единственный источник истины** для membership (устройство ↔ группа).
     *
     * Важно:
     * - В таблице **нет `projectId`**.
     * - Поэтому любые массовые операции должны быть ограничены списком `group_id`,
     *   полученным из `groups` конкретного `projectId`.
     */
    // -------------------- OBSERVE --------------------

    @Query("SELECT * FROM group_device_join")
    fun observeJoins(): Flow<List<GroupDeviceJoin>>

    // -------------------- READ (dbState для diff-commit) --------------------

    @Query("SELECT * FROM group_device_join WHERE group_id IN (:groupIds)")
    suspend fun getJoinsForGroupIds(groupIds: List<Long>): List<GroupDeviceJoin>

    // -------------------- INSERT --------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJoin(join: GroupDeviceJoin)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(joins: List<GroupDeviceJoin>)

    // -------------------- READ helpers --------------------

    @Query("SELECT device_id FROM group_device_join WHERE group_id = :groupId")
    suspend fun getDeviceIdsForGroup(groupId: Long): List<Long>

    @Query("SELECT group_id FROM group_device_join WHERE device_id = :deviceId")
    suspend fun getGroupIdsForDevice(deviceId: Long): List<Long>

    @Query(
        """
        SELECT d.* FROM devices d
        INNER JOIN group_device_join j ON j.device_id = d.device_id
        WHERE j.group_id = :groupId
        """
    )
    suspend fun getDevicesForGroup(groupId: Long): List<DeviceEntity>

    // -------------------- DELETE helpers --------------------

    @Query("DELETE FROM group_device_join")
    suspend fun deleteAll(): Int

    @Query("DELETE FROM group_device_join WHERE device_id = :deviceId")
    suspend fun deleteJoinsForDevice(deviceId: Long): Int

    @Query("DELETE FROM group_device_join WHERE device_id IN (:deviceIds)")
    suspend fun deleteJoinsForDeviceIds(deviceIds: List<Long>): Int

    @Query("DELETE FROM group_device_join WHERE group_id = :groupId")
    suspend fun deleteJoinsForGroupId(groupId: Long): Int

    @Query(
        """
        DELETE FROM group_device_join
        WHERE group_id IN (SELECT group_id FROM `groups` WHERE room_id = :roomId)
        """
    )
    suspend fun deleteJoinsForRoom(roomId: Long): Int

    /**
     * ✅ Теперь возвращаем rowsDeleted, чтобы сравнивать с expected.
     */
    @Query("DELETE FROM group_device_join WHERE group_id IN (:groupIds)")
    suspend fun deleteJoinsForGroupIds(groupIds: List<Long>): Int
}